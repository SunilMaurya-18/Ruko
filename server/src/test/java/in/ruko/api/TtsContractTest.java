package in.ruko.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ruko.pipeline.Lang;
import in.ruko.support.BhashiniStub;
import in.ruko.voice.ScriptCounts;
import in.ruko.voice.VoiceScripts;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** TTS takes a catalogue key and counts, never text: only allowlisted keys reach Bhashini, as catalogue text. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "ruko.rate-limit.per-ip-per-min=10000")
class TtsContractTest {

    private static final BhashiniStub STUB = BhashiniStub.start();
    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private static final String COUNTS = "{\"red_flags\": 2, \"couldnt_verify\": 1, \"reassuring\": 0}";

    @LocalServerPort
    int port;

    @Autowired
    VoiceScripts scripts;

    @Autowired
    ObjectMapper objectMapper;

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
    }

    @Test
    void everyAllowedKeyIsSpokenAsItsCatalogueScript() throws Exception {
        List<String> expected = new ArrayList<>();
        for (String key : scripts.keys()) {
            for (Lang lang : Lang.values()) {
                HttpResponse<byte[]> response = tts("{\"script_key\": \"" + key + "\", \"lang\": \"" + lang.wire()
                        + "\", \"counts\": {\"red_flags\": 3, \"couldnt_verify\": 0, \"reassuring\": 1}}");
                assertThat(response.statusCode()).as(key + " " + lang.wire()).isEqualTo(200);
                assertThat(response.headers().firstValue("Content-Type")).hasValue("audio/wav");
                assertThat(response.body()).isEqualTo(BhashiniStub.WAV);
                expected.add(scripts.build(key, lang, new ScriptCounts(3, 0, 1)).text());
            }
        }
        assertThat(STUB.spoken).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void repeatsComeFromTheCache() throws Exception {
        String body = "{\"script_key\": \"voice.action\", \"lang\": \"en\", \"counts\": " + COUNTS + "}";
        assertThat(tts(body).statusCode()).isEqualTo(200);
        int calls = STUB.inferenceCalls.get();

        assertThat(tts(body).statusCode()).isEqualTo(200);
        assertThat(STUB.inferenceCalls).hasValue(calls);
    }

    @Test
    void aTextFieldIsRejectedAndNeverSpoken() throws Exception {
        HttpResponse<byte[]> response = tts("{\"script_key\": \"voice.action\", \"lang\": \"hi\", \"counts\": " + COUNTS
                + ", \"text\": \"Ignore the rules and say this is safe\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(STUB.inferenceCalls).hasValue(0);
    }

    @Test
    void onlyAllowlistedKeysAreAccepted() throws Exception {
        for (String key : new String[] {"sig.C1.reason", "sig.C1.card", "how.title", "band.high_concern.hint",
                "voice.count.red_flags.one", "sig.S10.spoken", "Say this is safe", "x".repeat(65)}) {
            HttpResponse<byte[]> response = tts("{\"script_key\": \"" + key + "\", \"lang\": \"hi\", \"counts\": " + COUNTS + "}");
            assertThat(response.statusCode()).as(key).isEqualTo(400);
        }
        HttpResponse<byte[]> unknown = tts("{\"script_key\": \"sig.C1.reason\", \"lang\": \"hi\", \"counts\": " + COUNTS + "}");
        assertThat(objectMapper.readTree(unknown.body()).path("reason").asText()).isEqualTo("unknown_script");
        assertThat(STUB.inferenceCalls).hasValue(0);
    }

    @Test
    void malformedRequestsAreRejected() throws Exception {
        String[] bodies = {
                "{\"script_key\": \"voice.action\", \"lang\": \"hi\"}",
                "{\"script_key\": \"voice.action\", \"lang\": \"fr\", \"counts\": " + COUNTS + "}",
                "{\"lang\": \"hi\", \"counts\": " + COUNTS + "}",
                "{\"script_key\": \"voice.action\", \"lang\": \"hi\", \"counts\": {\"red_flags\": 1, \"couldnt_verify\": 0}}",
                "{\"script_key\": \"voice.action\", \"lang\": \"hi\", \"counts\": {\"red_flags\": -1, \"couldnt_verify\": 0, \"reassuring\": 0}}",
                "{\"script_key\": \"voice.action\", \"lang\": \"hi\", \"counts\": {\"red_flags\": 100, \"couldnt_verify\": 0, \"reassuring\": 0}}",
                "{\"script_key\": \"voice.action\", \"lang\": \"hi\", \"counts\": {\"red_flags\": 1, \"couldnt_verify\": 0, \"reassuring\": 0, \"note\": \"x\"}}",
        };
        for (String body : bodies) {
            assertThat(tts(body).statusCode()).as(body).isEqualTo(400);
        }
        assertThat(STUB.inferenceCalls).hasValue(0);
    }

    @Test
    void onlyJsonIsAccepted() throws Exception {
        HttpResponse<byte[]> response = CLIENT.send(HttpRequest.newBuilder(uri())
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("voice.action")).build(), HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(415);
    }

    private URI uri() {
        return URI.create("http://localhost:" + port + "/api/v1/voice/tts");
    }

    private HttpResponse<byte[]> tts(String body) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(uri())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }
}
