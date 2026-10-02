package in.ruko.voice;

/** The TTS request named a key outside the spoken-script allowlist. Carries no request text. */
public class UnknownScriptException extends RuntimeException {

    public UnknownScriptException() {
        super("unknown script key", null, false, false);
    }
}
