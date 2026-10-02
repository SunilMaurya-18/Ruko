package in.ruko.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.ruko.support.Canary;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Log audit: every fixture and every edge-case probe (with a canary appended) goes through the analyze pipeline over
 * HTTP, and every error path
 * gets a canary in each client-controlled place. The canary must never reach log output.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "ruko.rate-limit.per-ip-per-min=10000")
class NoContentLoggingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    int port;

    @Test
    void canaryNeverAppearsInLogs(CapturedOutput output) throws Exception {
        URI base = URI.create("http://localhost:" + port);
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        JsonNode fixtures = read("fixtures/fixtures.v0.json");
        JsonNode probes = read("fixtures/engine-probes.v0.json");
        int analyzed = 0;
        for (JsonNode message : List.of(fixtures, probes)) {
            for (JsonNode fixture : message) {
                Map<String, Object> request = new LinkedHashMap<>();
                request.put("text", fixture.path("text").asText() + " " + Canary.VALUE);
                request.put("lang", fixture.path("lang").asText());
                request.put("source", fixture.path("source").asText());
                if (fixture.has("ocr_confidence")) {
                    request.put("ocr_confidence", fixture.path("ocr_confidence").asDouble());
                }
                int status = client.send(Canary.analyze(base, JSON.writeValueAsString(request)),
                        BodyHandlers.discarding()).statusCode();
                assertThat(status).as(fixture.path("id").asText()).isEqualTo(200);
                analyzed++;
            }
        }
        for (Canary.Case testCase : Canary.cases()) {
            Canary.send(base, testCase);
        }
        Canary.sendInvalidRequestTarget(port);

        assertThat(fixtures.size()).isGreaterThanOrEqualTo(80);
        assertThat(analyzed).isEqualTo(fixtures.size() + probes.size());
        assertThat(output.getAll())
                .contains("event=" + LogEvent.ANALYZED)
                .contains("event=" + LogEvent.REQUEST_REJECTED)
                .contains("event=" + LogEvent.UNHANDLED_ERROR)
                .doesNotContain(Canary.VALUE);
    }

    private static JsonNode read(String resource) throws Exception {
        try (InputStream in = NoContentLoggingTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            return JSON.readTree(in);
        }
    }
}
