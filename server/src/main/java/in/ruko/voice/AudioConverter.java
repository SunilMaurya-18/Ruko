package in.ruko.voice;

import in.ruko.infra.config.VoiceProps;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * ASR uploads (WhatsApp {@code .opus} voice notes, browser recordings) to 16 kHz mono WAV through ffmpeg on
 * stdin/stdout. No temp files: audio exists only in this request's memory. ffmpeg may only read the pipe and a short
 * list of audio containers, output is capped at {@code asr-max-seconds}, stderr is discarded (it can echo metadata),
 * and a conversion that overruns its timeout is killed.
 */
@Component
public class AudioConverter implements DisposableBean {

    public static final int SAMPLE_RATE = 16_000;

    static final String FORMATS = "ogg,matroska,webm,wav,mp3,mov,mp4,m4a,aac";

    private final VoiceProps props;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AudioConverter(VoiceProps props) {
        this.props = props;
    }

    /**
     * @throws AudioRejectedException the upload is empty or ffmpeg could not decode it
     * @throws IOException ffmpeg could not be started or timed out
     */
    public byte[] toWav(byte[] audio) throws IOException, InterruptedException {
        if (audio == null || audio.length == 0) {
            throw new AudioRejectedException();
        }
        Process process = new ProcessBuilder(command())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        int maxPcmBytes = props.asrMaxSeconds() * SAMPLE_RATE * 2;
        Future<?> writer = executor.submit(() -> feed(process, audio));
        Future<byte[]> reader = executor.submit(() -> drain(process.getInputStream(), maxPcmBytes));
        byte[] pcm;
        try {
            pcm = reader.get(props.conversionTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!process.waitFor(props.conversionTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IOException("ffmpeg did not exit in time");
            }
        } catch (TimeoutException e) {
            throw new IOException("ffmpeg timed out");
        } catch (ExecutionException e) {
            throw new IOException("ffmpeg output could not be read");
        } finally {
            writer.cancel(true);
            process.destroyForcibly();
        }
        int length = Math.min(pcm.length, maxPcmBytes) & ~1;
        if (process.exitValue() != 0 || length == 0) {
            throw new AudioRejectedException();
        }
        return Wav.fromPcm16Mono(pcm, length, SAMPLE_RATE);
    }

    List<String> command() {
        return List.of(props.ffmpeg(), "-hide_banner", "-loglevel", "error",
                "-protocol_whitelist", "pipe", "-format_whitelist", FORMATS,
                "-i", "pipe:0",
                "-t", Integer.toString(props.asrMaxSeconds()), "-vn", "-sn", "-dn", "-map_metadata", "-1",
                "-ac", "1", "-ar", Integer.toString(SAMPLE_RATE), "-c:a", "pcm_s16le", "-f", "s16le", "pipe:1");
    }

    /** A decoder that gives up early closes the pipe; that is reported through the exit code, not here. */
    private static void feed(Process process, byte[] audio) {
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(audio);
        } catch (IOException ignored) {
            // Broken pipe: ffmpeg stopped reading.
        }
    }

    private static byte[] drain(InputStream stdout, int maxBytes) throws IOException {
        try (stdout) {
            byte[] pcm = stdout.readNBytes(maxBytes);
            stdout.transferTo(OutputStream.nullOutputStream());
            return pcm;
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
