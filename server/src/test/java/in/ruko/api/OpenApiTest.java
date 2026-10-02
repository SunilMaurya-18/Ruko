package in.ruko.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.ruko.support.CanaryProbeController;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiTest {

    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    static final Set<String> API = Set.of(
            "POST /api/v1/analyze",
            "POST /api/v1/voice/tts",
            "POST /api/v1/voice/asr",
            "GET /api/v1/content/how-ruko-decides",
            "GET /api/v1/content/recovery",
            "GET /api/v1/content/links",
            "POST /api/v1/complaint/draft");

    @LocalServerPort
    int port;

    @Test
    void documentsExactlyTheApiWithWireFieldNames() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode doc = JSON.readTree(response.body());

        Set<String> operations = new TreeSet<>();
        doc.path("paths").properties().forEach(path -> path.getValue().properties().forEach(
                operation -> operations.add(operation.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey())));
        operations.removeIf(operation -> operation.contains(CanaryProbeController.PATH));

        assertThat(operations).isEqualTo(new TreeSet<>(API));
        assertThat(doc.path("info").path("title").asText()).isEqualTo("Ruko API");
        assertThat(doc.path("info").path("description").asText()).contains("not investment advice");
        assertThat(doc.path("components").path("schemas").path("AnalyzeRequest").path("properties").has("ocr_confidence"))
                .as("snake_case field names").isTrue();
        assertThat(response.body().toLowerCase(Locale.ROOT)).doesNotContain("journal");
    }

    @Test
    void docsPageLoadsWithItsOwnStylePolicyOnly() throws Exception {
        HttpResponse<String> redirect = get("/swagger-ui.html");
        assertThat(redirect.statusCode()).isEqualTo(302);

        HttpResponse<String> page = get("/swagger-ui/index.html");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.headers().firstValue("Content-Security-Policy").orElseThrow())
                .contains("style-src 'self' 'unsafe-inline'").contains("script-src 'self' 'wasm-unsafe-eval'")
                .contains("connect-src 'self'");
        assertThat(get("/v3/api-docs").headers().firstValue("Content-Security-Policy").orElseThrow())
                .doesNotContain("unsafe-inline");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
