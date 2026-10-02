package in.ruko.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.ruko.infra.AsrGateFilter;
import in.ruko.support.BhashiniStub;
import in.ruko.support.Ffmpeg;
import in.ruko.support.Multipart;
import in.ruko.voice.AudioConverter;
import in.ruko.voice.Wav;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** ASR with the flag on: consent is required before audio is read, at most 2 MB, converted in memory, not kept. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ruko.rate-limit.per-ip-per-min=10000", "ruko.features.asr=true"})
class AsrGuardTest {

    private static final BhashiniStub STUB = BhashiniStub.start();
    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private static final int TWO_MB = 2 * 1024 * 1024;

    @LocalServerPort
    int port;

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
    void missingConsentIs400AndNothingIsTranscribed() throws Exception {
        HttpResponse<String> response = send("/api/v1/voice/asr", new byte[] {1, 2, 3}, null);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).path("reason").asText()).isEqualTo("consent_required");
        assertThat(STUB.inferenceCalls).hasValue(0);
    }

    @Test
    void wrongConsentVersionIs400() throws Exception {
        assertThat(send("/api/v1/voice/asr", new byte[] {1, 2, 3}, "voice-asr-v0").statusCode()).isEqualTo(400);
        assertThat(send("/api/v1/voice/asr", new byte[] {1, 2, 3}, "yes").statusCode()).isEqualTo(400);
    }

    @Test
    void pathParametersDoNotSkipTheConsentGate() throws Exception {
        assertThat(send("/api/v1/voice/asr;consent=yes", new byte[] {1, 2, 3}, null).statusCode()).isEqualTo(400);
        assertThat(STUB.inferenceCalls).hasValue(0);
    }

    @Test
    void uploadJustOverTwoMegabytesIs413() throws Exception {
        assertThat(send("/api/v1/voice/asr", new byte[TWO_MB + 1], AsrGateFilter.CONSENT).statusCode()).isEqualTo(413);
        assertThat(STUB.inferenceCalls).hasValue(0);
    }

    @Test
    void muchLargerUploadIsRefusedBeforeItIsRead() throws Exception {
        assertThat(send("/api/v1/voice/asr", new byte[3 * TWO_MB], AsrGateFilter.CONSENT).statusCode()).isEqualTo(413);
    }

    @Test
    void emptyAudioIs415() throws Exception {
        HttpResponse<String> response = send("/api/v1/voice/asr", new byte[0], AsrGateFilter.CONSENT);

        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(objectMapper.readTree(response.body()).path("reason").asText()).isEqualTo("unreadable_audio");
    }

    @Test
    void undecodableAudioIs415() throws Exception {
        assumeTrue(Ffmpeg.available(), "ffmpeg not installed");

        HttpResponse<String> response = send("/api/v1/voice/asr",
                "Guaranteed 5% daily returns, pay now".getBytes(StandardCharsets.UTF_8), AsrGateFilter.CONSENT);

        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(response.body()).doesNotContain("Guaranteed");
        assertThat(STUB.inferenceCalls).hasValue(0);
    }

    @Test
    void opusVoiceNoteIsConvertedAndTranscribed() throws Exception {
        assumeTrue(Ffmpeg.available(), "ffmpeg not installed");
        STUB.transcript = "guaranteed return join vip group";

        HttpResponse<String> response = send("/api/v1/voice/asr?lang=en", Ffmpeg.opus(2), AsrGateFilter.CONSENT);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("transcript").asText()).isEqualTo("guaranteed return join vip group");
        assertThat(body.size()).isEqualTo(1);

        byte[] heard = STUB.heard.getFirst();
        assertThat(Wav.isWav(heard)).isTrue();
        assertThat(ByteBuffer.wrap(heard).order(ByteOrder.LITTLE_ENDIAN).getInt(24)).isEqualTo(AudioConverter.SAMPLE_RATE);
    }

    private HttpResponse<String> send(String path, byte[] audio, String consent) throws Exception {
        return CLIENT.send(Multipart.audio(URI.create("http://localhost:" + port + path), audio, consent),
                HttpResponse.BodyHandlers.ofString());
    }
}
