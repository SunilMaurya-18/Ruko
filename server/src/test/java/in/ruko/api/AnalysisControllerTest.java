package in.ruko.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.ruko.support.Canary;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "ruko.rate-limit.per-ip-per-min=1000")
class AnalysisControllerTest {

    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void returnsTheFinalResponseShapeWithEntities() throws Exception {
        JsonNode body = analyze(Map.of(
                "text", "Guaranteed 5% daily returns! Pay ₹4999 to vipprofits@okaxis. Reg INH000012345. Call +91 98765 43210",
                "lang", "hi", "source", "share"));

        assertThat(fieldNames(body)).containsExactly("language", "entities", "signals", "unverified", "reassuring",
                "band", "content_class", "counts", "cards", "analogy_key", "footer_key", "engine");
        assertThat(body.path("language").asText()).isEqualTo("hi");
        assertThat(body.path("engine").asText()).isEqualTo("template");
        assertThat(body.path("footer_key").asText()).isEqualTo("no_flags_not_safe");
        assertThat(body.path("signals").isArray()).isTrue();
        assertThat(fieldNames(body.path("counts"))).containsExactly("red_flags", "couldnt_verify", "reassuring");

        JsonNode entities = body.path("entities");
        assertThat(entities.path("upi_ids").get(0).asText()).isEqualTo("vipprofits@okaxis");
        assertThat(entities.path("reg_numbers").get(0).asText()).isEqualTo("INH000012345");
        assertThat(entities.path("phone_count").asInt()).isEqualTo(1);
        assertThat(entities.path("return_claims").get(0).asText()).contains("5% daily");
        assertThat(body.toString()).doesNotContain("98765");
    }

    @Test
    void shortTextIsNotEnoughToJudge() throws Exception {
        JsonNode body = analyze(Map.of("text", "ok bhai", "lang", "hi", "source", "paste"));

        assertThat(body.path("band").asText()).isEqualTo("not_enough_to_judge");
    }

    @Test
    void lowOcrConfidenceIsNotEnoughToJudge() throws Exception {
        JsonNode body = analyze(Map.of("text", "Guaranteed profit, contact us now for details",
                "lang", "en", "source", "ocr", "ocr_confidence", 0.3));

        assertThat(body.path("band").asText()).isEqualTo("not_enough_to_judge");
    }

    @Test
    void tooLongTextReturnsReasonCode() throws Exception {
        HttpResponse<String> response = send(Map.of("text", "a".repeat(4001), "lang", "hi", "source", "paste"));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(objectMapper.readTree(response.body()).path("reason").asText()).isEqualTo("too_long");
    }

    private JsonNode analyze(Map<String, Object> request) throws Exception {
        HttpResponse<String> response = send(request);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> send(Map<String, Object> request) throws Exception {
        URI base = URI.create("http://localhost:" + port);
        return CLIENT.send(Canary.analyze(base, objectMapper.writeValueAsString(request)), BodyHandlers.ofString());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new java.util.ArrayList<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        return names;
    }
}
