package in.ruko.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import in.ruko.voice.Wav;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * A local stand-in for Bhashini's ULCA pipeline: {@code /config} returns an inference route, {@code /infer} does TTS
 * (a short silent WAV) or ASR (a fixed transcript). Records what it was asked to speak or transcribe.
 */
public final class BhashiniStub implements AutoCloseable {

    public static final String USER = "stub-user";
    public static final String KEY = "stub-ulca-key";
    public static final String INFERENCE_KEY = "stub-inference-key";
    public static final byte[] WAV = Wav.fromPcm16Mono(new byte[1600], 1600, 8000);

    public enum Mode { OK, DOWN, SLOW, BAD_AUDIO, UNTRUSTED_CALLBACK }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    public final AtomicInteger configCalls = new AtomicInteger();
    public final AtomicInteger inferenceCalls = new AtomicInteger();
    public final List<String> spoken = new CopyOnWriteArrayList<>();
    public final List<byte[]> heard = new CopyOnWriteArrayList<>();
    public volatile Mode mode = Mode.OK;
    public volatile String transcript = "stub transcript";

    private BhashiniStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/config", this::config);
        server.createContext("/infer", this::infer);
        server.start();
    }

    public static BhashiniStub start() {
        try {
            return new BhashiniStub();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String configUrl() {
        return base() + "/config";
    }

    public void register(DynamicPropertyRegistry registry) {
        registry.add("ruko.bhashini.config-url", this::configUrl);
        registry.add("ruko.bhashini.user-id", () -> USER);
        registry.add("ruko.bhashini.api-key", () -> KEY);
    }

    public void reset() {
        mode = Mode.OK;
        configCalls.set(0);
        inferenceCalls.set(0);
        spoken.clear();
        heard.clear();
    }

    private String base() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private void config(HttpExchange exchange) throws IOException {
        configCalls.incrementAndGet();
        if (!USER.equals(exchange.getRequestHeaders().getFirst("userID"))
                || !KEY.equals(exchange.getRequestHeaders().getFirst("ulcaApiKey"))) {
            reply(exchange, 401, "{}");
            return;
        }
        JsonNode task = JSON.readTree(exchange.getRequestBody()).path("pipelineTasks").path(0);
        String type = task.path("taskType").asText();
        String lang = task.path("config").path("language").path("sourceLanguage").asText();

        ObjectNode body = JSON.createObjectNode();
        ObjectNode config = body.putArray("pipelineResponseConfig").addObject().put("taskType", type)
                .putArray("config").addObject().put("serviceId", "stub/" + type + "/" + lang);
        config.putObject("language").put("sourceLanguage", lang);
        ObjectNode endpoint = body.putObject("pipelineInferenceAPIEndPoint")
                .put("callbackUrl", mode == Mode.UNTRUSTED_CALLBACK ? "https://evil.example/infer" : base() + "/infer");
        endpoint.putObject("inferenceApiKey").put("name", "Authorization").put("value", INFERENCE_KEY);
        reply(exchange, 200, JSON.writeValueAsString(body));
    }

    private void infer(HttpExchange exchange) throws IOException {
        inferenceCalls.incrementAndGet();
        if (!INFERENCE_KEY.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            reply(exchange, 401, "{}");
            return;
        }
        if (mode == Mode.DOWN) {
            reply(exchange, 500, "{\"detail\": \"down\"}");
            return;
        }
        if (mode == Mode.SLOW) {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        JsonNode request = JSON.readTree(exchange.getRequestBody());
        String type = request.path("pipelineTasks").path(0).path("taskType").asText();
        ObjectNode body = JSON.createObjectNode();
        ObjectNode result = body.putArray("pipelineResponse").addObject().put("taskType", type);
        if ("tts".equals(type)) {
            spoken.add(request.path("inputData").path("input").path(0).path("source").asText());
            byte[] audio = mode == Mode.BAD_AUDIO ? "this is not audio at all, just text".getBytes(StandardCharsets.UTF_8) : WAV;
            result.putArray("audio").addObject().put("audioContent", Base64.getEncoder().encodeToString(audio));
        } else {
            heard.add(Base64.getDecoder().decode(request.path("inputData").path("audio").path(0).path("audioContent").asText()));
            result.putArray("output").addObject().put("source", transcript);
        }
        reply(exchange, 200, JSON.writeValueAsString(body));
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
