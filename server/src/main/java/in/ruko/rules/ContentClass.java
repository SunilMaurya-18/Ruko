package in.ruko.rules;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum ContentClass {
    PROMOTION, EDUCATION, MIXED, UNKNOWN;

    @JsonValue
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
