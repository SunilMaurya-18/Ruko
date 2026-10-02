package in.ruko.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import in.ruko.rules.RulesReadiness;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReadinessDownTest {

    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort
    int port;

    @MockitoBean
    RulesReadiness readiness;

    @Test
    void aFailingProbeTakesTheInstanceOutOfRotationButKeepsItAlive(CapturedOutput output) throws Exception {
        when(readiness.failures()).thenReturn(List.of("sc-002"));

        HttpResponse<String> response = get("/actuator/health/readiness");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).isEqualTo("{\"status\":\"DOWN\"}");
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(output).contains("event=READINESS_FAILED probe=sc-002");
    }

    @Test
    void rulesThatNoLongerCompileAreNotReady(CapturedOutput output) throws Exception {
        when(readiness.failures()).thenThrow(new IllegalStateException("rules/signals.v0.json: duplicate id C1"));

        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(503);
        assertThat(output).contains("event=READINESS_FAILED error_type=IllegalStateException")
                .doesNotContain("duplicate id");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
