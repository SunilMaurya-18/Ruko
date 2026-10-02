package in.ruko.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import in.ruko.infra.config.VoiceProps;
import in.ruko.support.Ffmpeg;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AudioConverterTest {

    private static AudioConverter converter(int maxSeconds) {
        return new AudioConverter(new VoiceProps("ffmpeg", maxSeconds, Duration.ofSeconds(10)));
    }

    @Test
    void opusVoiceNoteBecomesSixteenKilohertzMonoWav() throws Exception {
        assumeTrue(Ffmpeg.available(), "ffmpeg not installed");

        byte[] wav = converter(60).toWav(Ffmpeg.opus(2));

        assertThat(Wav.isWav(wav)).isTrue();
        ByteBuffer header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(header.getShort(22)).as("channels").isEqualTo((short) 1);
        assertThat(header.getInt(24)).as("sample rate").isEqualTo(AudioConverter.SAMPLE_RATE);
        assertThat(header.getShort(34)).as("bits").isEqualTo((short) 16);
        assertThat(header.getInt(40)).as("data size").isEqualTo(wav.length - Wav.HEADER_BYTES);
        assertThat(wav.length - Wav.HEADER_BYTES).isBetween(60_000, 68_000);
    }

    @Test
    void keepsAtMostTheConfiguredSeconds() throws Exception {
        assumeTrue(Ffmpeg.available(), "ffmpeg not installed");

        byte[] wav = converter(1).toWav(Ffmpeg.opus(3));

        assertThat(wav.length - Wav.HEADER_BYTES).isEqualTo(AudioConverter.SAMPLE_RATE * 2);
    }

    @Test
    void undecodableUploadIsRejected() {
        assumeTrue(Ffmpeg.available(), "ffmpeg not installed");

        assertThatThrownBy(() -> converter(60).toWav("not audio, just a typed message".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AudioRejectedException.class);
    }

    @Test
    void emptyUploadIsRejectedWithoutStartingFfmpeg() {
        AudioConverter missing = new AudioConverter(new VoiceProps("no-such-ffmpeg-binary", 60, Duration.ofSeconds(10)));

        assertThatThrownBy(() -> missing.toWav(new byte[0])).isInstanceOf(AudioRejectedException.class);
    }

    @Test
    void missingFfmpegIsAnIoErrorNotARejection() {
        AudioConverter missing = new AudioConverter(new VoiceProps("no-such-ffmpeg-binary", 60, Duration.ofSeconds(10)));

        assertThatThrownBy(() -> missing.toWav(new byte[] {1, 2, 3})).isInstanceOf(IOException.class);
    }

    @Test
    void ffmpegReadsOnlyThePipeAndWritesOnlyThePipe() {
        assertThat(converter(60).command())
                .containsSequence("-protocol_whitelist", "pipe")
                .containsSequence("-i", "pipe:0")
                .endsWith("pipe:1")
                .contains("-map_metadata");
    }
}
