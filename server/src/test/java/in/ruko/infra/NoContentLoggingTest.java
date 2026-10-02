package in.ruko.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.ruko.support.Canary;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Log audit: every fixture (with a canary appended) goes through the analyze pipeline, and every error path
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

        int analyzed = 0;
        for (JsonNode fixture : fixtures()) {
            String body = JSON.writeValueAsString(Map.of(
                    "text", fixture.path("text").asText() + " " + Canary.VALUE,
                    "lang", fixture.path("lang").asText(),
                    "source", fixture.path("source").asText()));
            int status = client.send(Canary.analyze(base, body), BodyHandlers.discarding()).statusCode();
            assertThat(status).as(fixture.path("id").asText()).isEqualTo(200);
            analyzed++;
        }
        for (Canary.Case testCase : Canary.cases()) {
            Canary.send(base, testCase);
        }
        Canary.sendInvalidRequestTarget(port);

        assertThat(analyzed).isGreaterThanOrEqualTo(40);
        assertThat(output.getAll())
                .contains("event=" + LogEvent.ANALYZED)
                .contains("event=" + LogEvent.REQUEST_REJECTED)
                .contains("event=" + LogEvent.UNHANDLED_ERROR)
                .doesNotContain(Canary.VALUE);
    }

    private static JsonNode fixtures() throws Exception {
        try (InputStream in = NoContentLoggingTest.class.getClassLoader().getResourceAsStream("fixtures/fixtures.v0.json")) {
            return JSON.readTree(in);
        }
    }
}
