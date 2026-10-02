package in.ruko.snapshot;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** What a dated snapshot says about a registration number. There is deliberately no "verified" value. */
public enum SnapshotStatus {
    LISTED, NOT_LISTED;

    @JsonValue
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
