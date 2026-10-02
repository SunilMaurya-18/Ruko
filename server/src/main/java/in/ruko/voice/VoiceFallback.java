package in.ruko.voice;

/** Why a voice call fell back. Logged as a tag; never carries content. */
public enum VoiceFallback {
    NOT_CONFIGURED,
    CIRCUIT_OPEN,
    TIMEOUT,
    ERROR,
    BAD_AUDIO,
    CONVERTER
}
