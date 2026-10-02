package in.ruko.api;

import static org.assertj.core.api.Assertions.assertThat;

import in.ruko.support.Canary;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Cut item #5: with {@code ruko.features.complaint=false} the draft route answers 404 and the PWA drafts on the phone. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ruko.rate-limit.per-ip-per-min=1000", "ruko.features.complaint=false"})
class ComplaintFlagOffTest {

    @LocalServerPort
    int port;

    @Test
    void complaintDraftIsNotFoundWhenTheFlagIsOff() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + Canary.COMPLAINT_PATH))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(Canary.COMPLAINT)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(404);
    }
}
