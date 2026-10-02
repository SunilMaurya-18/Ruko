package in.ruko.voice;

import in.ruko.pipeline.Lang;
import java.time.Duration;

/** Speech provider adapter. Callers add the timeout, circuit breaker, and fallback; adapters only do the call. */
public interface VoicePort {

    /** Catalogue text in, a complete WAV file out. */
    byte[] synthesize(String text, Lang lang, Duration timeout) throws Exception;

    /** 16 kHz mono 16-bit WAV in, transcript out. */
    String transcribe(byte[] wav, Lang lang, Duration timeout) throws Exception;
}
