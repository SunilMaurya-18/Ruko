package in.ruko.voice;

/**
 * Bhashini (or ffmpeg) could not serve this request. {@code fallback} tells the client what to use instead, such as
 * {@code browser_tts}; it is null when there is no fallback to name.
 */
public class VoiceUnavailableException extends RuntimeException {

    public static final String BROWSER_TTS = "browser_tts";

    private final String fallback;

    public VoiceUnavailableException(String fallback) {
        super("voice unavailable", null, false, false);
        this.fallback = fallback;
    }

    public String fallback() {
        return fallback;
    }
}
