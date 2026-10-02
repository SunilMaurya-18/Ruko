package in.ruko.rules;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Declaration order is display order. Only the first three count toward the band. */
public enum Severity {
    CRITICAL, STRONG, MODERATE, UNVERIFIED, REASSURANCE;

    public boolean countsTowardBand() {
        return this == CRITICAL || this == STRONG || this == MODERATE;
    }

    @JsonValue
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
