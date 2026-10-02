package in.ruko.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.ruko.infra.config.BhashiniProps;
import in.ruko.pipeline.Lang;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * {@link VoicePort} for Bhashini's ULCA pipeline. A config call (user id and ULCA key) returns the inference endpoint,
 * its key, and the service id per task and language; that route is cached for a while. The compute call then does
 * TTS or ASR. The inference endpoint must be on {@code *.bhashini.gov.in} or the config host, plain HTTP is refused
 * except to localhost, redirects are not followed, and reply sizes are capped. Errors carry a status, never a body.
 */
@Component
public class BhashiniClient implements VoicePort {

    static final int TTS_SAMPLE_RATE = 8000;
    static final Duration ROUTE_TTL = Duration.ofMinutes(30);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_CONFIG_BYTES = 256 * 1024;
    private static final int MAX_TTS_BYTES = 8 * 1024 * 1024;
    private static final int MAX_ASR_BYTES = 64 * 1024;
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");
    private static final String TRUSTED_SUFFIX = ".bhashini.gov.in";
    private static final Pattern HEADER_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9-]{0,63}");

    private record Route(URI endpoint, String keyName, String keyValue, String serviceId, long expiresAt) {
        @Override
        public String toString() {
            return "Route[redacted]";
        }
    }

    private final BhashiniProps props;
    private final HttpClient http;
    private final Map<String, Route> routes = new ConcurrentHashMap<>();

    public BhashiniClient(BhashiniProps props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(props.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public byte[] synthesize(String text, Lang lang, Duration timeout) throws IOException, InterruptedException {
        Route route = route("tts", lang, timeout);
        ObjectNode body = MAPPER.createObjectNode();
        task(body, "tts", lang, route).put("gender", "female").put("samplingRate", TTS_SAMPLE_RATE);
        body.putObject("inputData").putArray("input").addObject().put("source", text);

        JsonNode audio = compute("tts", lang, route, body, timeout, MAX_TTS_BYTES)
                .path("audio").path(0).path("audioContent");
        if (!audio.isTextual() || audio.asText().isEmpty()) {
            throw new IOException("Bhashini TTS reply has no audio");
        }
        try {
            return Base64.getMimeDecoder().decode(audio.asText());
        } catch (IllegalArgumentException e) {
            throw new IOException("Bhashini TTS audio is not base64");
        }
    }

    @Override
    public String transcribe(byte[] wav, Lang lang, Duration timeout) throws IOException, InterruptedException {
        Route route = route("asr", lang, timeout);
        ObjectNode body = MAPPER.createObjectNode();
        task(body, "asr", lang, route).put("audioFormat", "wav").put("samplingRate", AudioConverter.SAMPLE_RATE);
        body.putObject("inputData").putArray("audio").addObject()
                .put("audioContent", Base64.getEncoder().encodeToString(wav));

        JsonNode transcript = compute("asr", lang, route, body, timeout, MAX_ASR_BYTES)
                .path("output").path(0).path("source");
        if (!transcript.isTextual()) {
            throw new IOException("Bhashini ASR reply has no transcript");
        }
        return transcript.asText();
    }

    private static ObjectNode task(ObjectNode body, String task, Lang lang, Route route) {
        ObjectNode config = body.putArray("pipelineTasks").addObject().put("taskType", task).putObject("config");
        config.putObject("language").put("sourceLanguage", lang.wire());
        return config.put("serviceId", route.serviceId());
    }

    /** Returns the pipeline response element for {@code task}. A failed call drops the cached route. */
    private JsonNode compute(String task, Lang lang, Route route, ObjectNode body, Duration timeout, int maxBytes)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(route.endpoint())
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header(route.keyName(), route.keyValue())
                .POST(HttpRequest.BodyPublishers.ofByteArray(MAPPER.writeValueAsBytes(body)))
                .build();
        try {
            for (JsonNode element : send(request, maxBytes, "inference").path("pipelineResponse")) {
                if (task.equals(element.path("taskType").asText())) {
                    return element;
                }
            }
            throw new IOException("Bhashini reply has no " + task + " result");
        } catch (IOException e) {
            routes.remove(routeKey(task, lang), route);
            throw e;
        }
    }

    private Route route(String task, Lang lang, Duration timeout) throws IOException, InterruptedException {
        String key = routeKey(task, lang);
        Route cached = routes.get(key);
        if (cached != null && cached.expiresAt() - System.nanoTime() > 0) {
            return cached;
        }
        URI configUri = checkedUri(props.configUrl());
        ObjectNode body = MAPPER.createObjectNode();
        body.putArray("pipelineTasks").addObject().put("taskType", task)
                .putObject("config").putObject("language").put("sourceLanguage", lang.wire());
        body.putObject("pipelineRequestConfig").put("pipelineId", props.pipelineId());
        HttpRequest request = HttpRequest.newBuilder(configUri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("userID", props.userId())
                .header("ulcaApiKey", props.apiKey())
                .POST(HttpRequest.BodyPublishers.ofByteArray(MAPPER.writeValueAsBytes(body)))
                .build();
        JsonNode reply = send(request, MAX_CONFIG_BYTES, "config");

        JsonNode endpoint = reply.path("pipelineInferenceAPIEndPoint");
        String keyName = endpoint.path("inferenceApiKey").path("name").asText("");
        String keyValue = endpoint.path("inferenceApiKey").path("value").asText("");
        if (!HEADER_NAME.matcher(keyName).matches() || keyValue.isBlank()) {
            throw new IOException("Bhashini config reply has no usable inference key");
        }
        Route route = new Route(inferenceUri(endpoint.path("callbackUrl").asText(""), configUri), keyName, keyValue,
                serviceId(reply, task, lang), System.nanoTime() + ROUTE_TTL.toNanos());
        routes.put(key, route);
        return route;
    }

    private static String serviceId(JsonNode reply, String task, Lang lang) throws IOException {
        String fallback = null;
        for (JsonNode taskConfig : reply.path("pipelineResponseConfig")) {
            if (!task.equals(taskConfig.path("taskType").asText())) {
                continue;
            }
            for (JsonNode config : taskConfig.path("config")) {
                String serviceId = config.path("serviceId").asText("");
                if (serviceId.isBlank()) {
                    continue;
                }
                if (lang.wire().equals(config.path("language").path("sourceLanguage").asText())) {
                    return serviceId;
                }
                fallback = Objects.requireNonNullElse(fallback, serviceId);
            }
        }
        if (fallback == null) {
            throw new IOException("Bhashini config reply has no " + task + " service");
        }
        return fallback;
    }

    private JsonNode send(HttpRequest request, int maxBytes, String stage) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("Bhashini " + stage + " returned status " + response.statusCode());
            }
            byte[] bytes = in.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) {
                throw new IOException("Bhashini " + stage + " reply is too large");
            }
            return MAPPER.readTree(bytes);
        }
    }

    private static String routeKey(String task, Lang lang) {
        return task + ":" + lang.wire();
    }

    private static URI checkedUri(String value) throws IOException {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IOException("Bhashini URL is not valid");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean secure = "https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && LOCAL_HOSTS.contains(host));
        if (!secure || host.isEmpty() || uri.getRawUserInfo() != null) {
            throw new IOException("Bhashini URL must use https");
        }
        return uri;
    }

    /** The config reply names the inference endpoint; only Bhashini's own hosts, or the config host, are trusted. */
    private static URI inferenceUri(String value, URI configUri) throws IOException {
        URI uri = checkedUri(value);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean bhashini = "https".equals(uri.getScheme()) && host.endsWith(TRUSTED_SUFFIX);
        boolean sameHost = uri.getScheme().equals(configUri.getScheme())
                && host.equals(configUri.getHost().toLowerCase(Locale.ROOT)) && uri.getPort() == configUri.getPort();
        if (!bhashini && !sameHost) {
            throw new IOException("Bhashini inference endpoint is not a trusted host");
        }
        return uri;
    }
}
