package in.ruko.explain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.ruko.infra.config.LlmProps;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * {@link LlmPort} for any OpenAI-compatible {@code /chat/completions} endpoint (configured by {@code ruko.llm.*}):
 * temperature 0, JSON-object reply mode. Plain HTTP is refused except to localhost. Errors carry a status code, never
 * the response body.
 */
@Component
public class ChatCompletionsClient implements LlmPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private final LlmProps props;
    private final HttpClient http;

    public ChatCompletionsClient(LlmProps props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(props.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String complete(LlmPrompt prompt, Duration timeout) throws IOException, InterruptedException {
        URI endpoint = endpoint();
        ObjectNode body = MAPPER.createObjectNode()
                .put("model", props.model())
                .put("temperature", 0);
        body.putObject("response_format").put("type", "json_object");
        body.putArray("messages")
                .add(MAPPER.createObjectNode().put("role", "system").put("content", prompt.system()))
                .add(MAPPER.createObjectNode().put("role", "user").put("content", prompt.user()));

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("LLM provider returned status " + response.statusCode());
        }
        JsonNode content = MAPPER.readTree(response.body()).path("choices").path(0).path("message").path("content");
        if (!content.isTextual()) {
            throw new IOException("LLM provider reply has no message content");
        }
        return content.asText();
    }

    private URI endpoint() throws IOException {
        String base = props.baseUrl().endsWith("/") ? props.baseUrl() : props.baseUrl() + "/";
        URI uri = URI.create(base).resolve("chat/completions");
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme()) && LOCAL_HOSTS.contains(host))) {
            throw new IOException("LLM base URL must use https");
        }
        return uri;
    }
}
