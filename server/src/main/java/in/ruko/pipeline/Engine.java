package in.ruko.pipeline;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum Engine {
    TEMPLATE, LLM, ON_DEVICE;

    @JsonValue
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
