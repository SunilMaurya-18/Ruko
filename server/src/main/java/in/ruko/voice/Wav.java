package in.ruko.voice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Minimal RIFF/WAVE helpers: a sanity check for provider audio and a header for raw 16-bit mono PCM. */
public final class Wav {

    public static final int HEADER_BYTES = 44;

    private Wav() {
    }

    /** True when {@code bytes} starts with a RIFF/WAVE header and carries more than the header. */
    public static boolean isWav(byte[] bytes) {
        return bytes != null && bytes.length > HEADER_BYTES
                && "RIFF".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII))
                && "WAVE".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII));
    }

    /** Wraps little-endian 16-bit mono PCM in a WAV container with exact sizes. */
    public static byte[] fromPcm16Mono(byte[] pcm, int length, int sampleRate) {
        ByteBuffer out = ByteBuffer.allocate(HEADER_BYTES + length).order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + length)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) 1)
                .putInt(sampleRate).putInt(sampleRate * 2)
                .putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(length)
                .put(pcm, 0, length);
        return out.array();
    }
}
