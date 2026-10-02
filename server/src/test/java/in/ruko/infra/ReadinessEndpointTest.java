package in.ruko.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReadinessEndpointTest {

    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort
    int port;

    @Test
    void readyWhenRulesCompileAndProbesPass() throws Exception {
        HttpResponse<String> readiness = get("/actuator/health/readiness");

        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(readiness.body()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health").body()).startsWith("{\"status\":\"UP\"").doesNotContain("components");
    }

    @Test
    void onlyHealthIsExposed() throws Exception {
        for (String path : new String[] {"/actuator/env", "/actuator/metrics", "/actuator/info", "/actuator/beans"}) {
            assertThat(get(path).statusCode()).as(path).isEqualTo(404);
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
