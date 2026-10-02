package in.ruko.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "ruko.rate-limit.per-ip-per-min=1000")
class ContentControllerTest {

    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void howRukoDecidesListsIdsReasonsLimitsAndSnapshotDate() throws Exception {
        HttpResponse<String> response = get("/api/v1/content/how-ruko-decides?lang=hi");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode page = objectMapper.readTree(response.body());

        assertThat(page.path("language").asText()).isEqualTo("hi");
        assertThat(page.path("title").asText()).isEqualTo("रुको कैसे तय करता है");
        assertThat(page.path("bands").size()).isEqualTo(4);
        assertThat(page.path("limits").size()).isGreaterThanOrEqualTo(5);
        assertThat(page.has("snapshot_date")).isTrue();
        assertThat(page.path("snapshot_date").isNull()).isTrue();
        assertThat(page.path("snapshot_note").asText()).contains("SEBI Check");

        List<String> ids = new ArrayList<>();
        page.path("signals").forEach(signal -> {
            ids.add(signal.path("id").asText());
            assertThat(signal.path("reason").asText()).isNotBlank();
        });
        assertThat(ids).contains("C1", "C2", "U14", "R2").doesNotContain("S10", "S19");
    }

    @Test
    void pageCarriesNoPatternsOrLimits() throws Exception {
        String body = get("/api/v1/content/how-ruko-decides?lang=en").body();

        assertThat(body).doesNotContain("\\p{").doesNotContain("(?<").doesNotContain("[0-9]")
                .doesNotContain("anydesk").doesNotContain("per-ip").doesNotContain("per_ip")
                .doesNotContain("rate-limit").doesNotContain("rate_limit");
    }

    @Test
    void defaultsToHindiAndRejectsUnknownLanguages() throws Exception {
        assertThat(objectMapper.readTree(get("/api/v1/content/how-ruko-decides").body()).path("language").asText())
                .isEqualTo("hi");
        assertThat(get("/api/v1/content/how-ruko-decides?lang=fr").statusCode()).isEqualTo(400);
    }

    @Test
    void recoveryHasBothPathsWithAllowlistedLinks() throws Exception {
        HttpResponse<String> response = get("/api/v1/content/recovery?lang=hi");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode page = objectMapper.readTree(response.body());

        assertThat(page.path("language").asText()).isEqualTo("hi");
        assertThat(page.path("cyber_fraud").size()).isGreaterThanOrEqualTo(3);
        assertThat(page.path("scores").size()).isGreaterThanOrEqualTo(2);
        JsonNode first = page.path("cyber_fraud").get(0);
        assertThat(first.path("text").asText()).contains("1930");
        assertThat(first.path("link").path("url").asText()).isEqualTo("tel:1930");
        assertThat(first.path("link").path("label").asText()).isNotBlank();
        assertThat(page.path("cyber_fraud").get(1).has("link")).isFalse();
        assertThat(response.body()).contains("https://cybercrime.gov.in").contains("https://scores.sebi.gov.in");
        assertThat(get("/api/v1/content/recovery?lang=fr").statusCode()).isEqualTo(400);
    }

    @Test
    void linksListsOnlyTheAllowlist() throws Exception {
        JsonNode page = objectMapper.readTree(get("/api/v1/content/links?lang=en").body());

        List<String> urls = new ArrayList<>();
        page.path("links").forEach(link -> urls.add(link.path("url").asText()));
        assertThat(urls).containsExactlyInAnyOrder("tel:1930", "https://cybercrime.gov.in",
                "https://scores.sebi.gov.in", "https://siportal.sebi.gov.in/intermediary/sebi-check");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
