package in.ruko.rules;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum Band {
    HIGH_CONCERN, SOME_CONCERN, FEW_FLAGS_STILL_VERIFY, NOT_ENOUGH_TO_JUDGE;

    @JsonValue
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
