package in.ruko.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.ruko.support.Canary;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "ruko.rate-limit.per-ip-per-min=1000")
class ComplaintControllerTest {

    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void draftsFromStructuredFacts() throws Exception {
        HttpResponse<String> response = post(Canary.COMPLAINT);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode draft = objectMapper.readTree(response.body());
        assertThat(draft.path("language").asText()).isEqualTo("hi");
        assertThat(draft.path("text").asText()).contains("01-09-2026").contains("₹4,999").contains("WhatsApp");
        assertThat(draft.path("words").asInt()).isBetween(1, 200);
    }

    @Test
    void refusesFreeTextAndBadFactsWithoutEchoingThem() throws Exception {
        String note = "my own words " + Canary.VALUE;
        for (String body : new String[] {
                Canary.COMPLAINT.replace("}", ", \"description\": \"" + note + "\"}"),
                Canary.COMPLAINT.replace("\"upi\"", "\"" + note + "\""),
                Canary.COMPLAINT.replace("4999", "\"" + note + "\""),
                Canary.COMPLAINT.replace("4999", "0"),
                Canary.COMPLAINT.replace("4999", "1000000001"),
                Canary.COMPLAINT.replace(", \"platform\": \"whatsapp\"", ""),
                Canary.COMPLAINT.replace("2026-09-01", note)}) {
            HttpResponse<String> response = post(body);
            assertThat(response.statusCode()).as(body).isEqualTo(400);
            assertThat(response.body()).doesNotContain(Canary.VALUE).doesNotContain("my own words");
        }
    }

    @Test
    void futureDatesAreRefused() throws Exception {
        HttpResponse<String> response = post(Canary.COMPLAINT.replace("2026-09-01", "2999-01-01"));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).path("reason").asText()).isEqualTo("invalid_date");
    }

    private HttpResponse<String> post(String body) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + Canary.COMPLAINT_PATH))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
