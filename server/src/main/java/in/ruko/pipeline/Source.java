package in.ruko.pipeline;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum Source {
    SHARE, PASTE, OCR, ASR, VIDEO, AUDIO;

    @JsonValue
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
