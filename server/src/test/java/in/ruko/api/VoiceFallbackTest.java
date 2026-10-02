package in.ruko.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.ruko.support.BhashiniStub;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Bhashini down, slow, or wrong: a 503 naming browser_tts within about the 3 s budget, and a fallback count. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "ruko.rate-limit.per-ip-per-min=10000")
class VoiceFallbackTest {

    private static final BhashiniStub STUB = BhashiniStub.start();
    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private Duration lastTook = Duration.ZERO;

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MeterRegistry registry;

    @Autowired
    @Qualifier("bhashiniTtsCircuitBreaker")
    CircuitBreaker breaker;

    @DynamicPropertySource
    static void bhashini(DynamicPropertyRegistry registry) {
        STUB.register(registry);
    }

    @AfterAll
    static void stop() {
        STUB.close();
    }

    @BeforeEach
    void reset() {
        STUB.reset();
        breaker.reset();
    }

    @Test
    void bhashiniDown() throws Exception {
        STUB.mode = BhashiniStub.Mode.DOWN;

        assertFallback(tts("voice.action"), Duration.ofSeconds(2));
        assertThat(STUB.inferenceCalls).hasValue(1);
    }

    @Test
    void bhashiniSlowIsCutOffAtTheThreeSecondBudget() throws Exception {
        STUB.mode = BhashiniStub.Mode.SLOW;

        long started = System.nanoTime();
        HttpResponse<String> response = tts("voice.generic");
        Duration took = Duration.ofNanos(System.nanoTime() - started);

        assertFallback(response, Duration.ofMillis(3_800));
        assertThat(took).isGreaterThanOrEqualTo(Duration.ofMillis(2_900));
    }

    @Test
    void bhashiniReturnsSomethingThatIsNotAudio() throws Exception {
        STUB.mode = BhashiniStub.Mode.BAD_AUDIO;

        assertFallback(tts("footer.no_flags_not_safe"), Duration.ofSeconds(2));
    }

    @Test
    void repeatedFailuresOpenTheCircuitAndStopCallingBhashini() throws Exception {
        STUB.mode = BhashiniStub.Mode.DOWN;
        for (String key : new String[] {"class.promotion", "class.education", "class.mixed", "class.unknown", "sig.C1.spoken"}) {
            assertFallback(tts(key), Duration.ofSeconds(2));
        }
        int calls = STUB.inferenceCalls.get();

        assertFallback(tts("sig.C2.spoken"), Duration.ofSeconds(1));
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(STUB.inferenceCalls).hasValue(calls);
    }

    private void assertFallback(HttpResponse<String> response, Duration within) throws Exception {
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("fallback").asText()).isEqualTo("browser_tts");
        assertThat(body.path("status").asInt()).isEqualTo(503);
        assertThat(lastTook).isLessThan(within);
    }

    private HttpResponse<String> tts(String key) throws Exception {
        double before = registry.counter("ruko.tts.fallback").count();
        long started = System.nanoTime();
        HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/voice/tts"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"script_key\": \"" + key
                        + "\", \"lang\": \"hi\", \"counts\": {\"red_flags\": 1, \"couldnt_verify\": 0, \"reassuring\": 0}}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        lastTook = Duration.ofNanos(System.nanoTime() - started);
        assertThat(registry.counter("ruko.tts.fallback").count()).isEqualTo(before + 1);
        return response;
    }
}
