package in.ruko.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.ruko.support.Canary;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "ruko.rate-limit.per-ip-per-min=1000")
class ErrorEchoTest {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    static List<Canary.Case> cases() {
        return Canary.cases();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void errorResponsesAreFixedProblemDetailsThatNeverEchoInput(Canary.Case testCase) throws Exception {
        HttpResponse<String> response = Canary.send(URI.create("http://localhost:" + port), testCase);

        assertThat(response.statusCode()).isEqualTo(testCase.expectedStatus());
        assertThat(response.body()).doesNotContain(Canary.VALUE);
        response.headers().map().forEach((name, values) ->
                assertThat(values).as("header %s", name).noneMatch(value -> value.contains(Canary.VALUE)));

        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        JsonNode problem = objectMapper.readTree(response.body());
        ProblemType expected = ProblemType.forStatus(org.springframework.http.HttpStatusCode.valueOf(testCase.expectedStatus()));
        assertThat(problem.path("status").asInt()).isEqualTo(testCase.expectedStatus());
        assertThat(problem.path("type").asText()).isEqualTo(expected.toProblemDetail().getType().toString());
        assertThat(problem.path("title").asText()).isEqualTo(expected.toProblemDetail().getTitle());
        assertThat(problem.path("detail").asText()).isEqualTo(expected.toProblemDetail().getDetail());

        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
    }

    @Test
    void requestLineRejectedByTomcatDoesNotEchoInput() throws Exception {
        String raw = Canary.sendInvalidRequestTarget(port);

        assertThat(raw).startsWith("HTTP/1.1 400");
        assertThat(raw).doesNotContain(Canary.VALUE);
    }
}
