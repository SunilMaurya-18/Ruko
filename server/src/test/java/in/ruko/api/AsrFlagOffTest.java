package in.ruko.api;

import static org.assertj.core.api.Assertions.assertThat;

import in.ruko.infra.AsrGateFilter;
import in.ruko.support.Multipart;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** With {@code ruko.features.asr=false} (the default) the ASR route does not exist, consent or not. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "ruko.rate-limit.per-ip-per-min=1000")
class AsrFlagOffTest {

    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort
    int port;

    @Test
    void asrIsNotFoundWhenTheFlagIsOff() throws Exception {
        URI uri = URI.create("http://localhost:" + port + "/api/v1/voice/asr");

        assertThat(CLIENT.send(Multipart.audio(uri, new byte[] {1, 2, 3}, AsrGateFilter.CONSENT),
                HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
        assertThat(CLIENT.send(Multipart.audio(uri, new byte[] {1, 2, 3}, null),
                HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
        assertThat(CLIENT.send(HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
    }
}
