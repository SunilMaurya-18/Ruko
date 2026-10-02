package in.ruko.pipeline;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Carries only a reason code; never the rejected text. */
public class InputRejectedException extends RuntimeException {

    public enum Reason {
        EMPTY, TOO_LONG, INVALID_ENCODING, BINARY;

        @JsonValue
        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Reason reason;

    public InputRejectedException(Reason reason) {
        super(reason.name(), null, false, false);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
