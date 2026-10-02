package in.ruko.pipeline;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum Lang {
    HI, EN;

    @JsonValue
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
