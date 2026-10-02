package in.ruko.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/** Test audio from the local ffmpeg. Tests that need it skip when ffmpeg is not installed. */
public final class Ffmpeg {

    private static final boolean AVAILABLE = probe();

    private Ffmpeg() {
    }

    public static boolean available() {
        return AVAILABLE;
    }

    /** A sine tone in an Ogg/Opus container, like a WhatsApp voice note. */
    public static byte[] opus(double seconds) {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "error",
                    "-f", "lavfi", "-i", "sine=frequency=440:duration=" + seconds,
                    "-c:a", "libopus", "-f", "ogg", "pipe:1")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            process.getOutputStream().close();
            byte[] audio;
            try (InputStream in = process.getInputStream()) {
                audio = in.readAllBytes();
            }
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("ffmpeg could not make test audio");
            }
            return audio;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static boolean probe() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            try (InputStream in = process.getInputStream()) {
                in.transferTo(OutputStream.nullOutputStream());
            }
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
