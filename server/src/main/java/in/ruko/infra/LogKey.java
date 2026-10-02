package in.ruko.infra;

import java.util.Locale;

public enum LogKey {
    STATUS,
    ERROR_TYPE,
    PROBLEM,
    REASON,
    SOURCE,
    DURATION_MS,
    COUNT,
    LIMIT,
    SIGNAL_ID,
    LINT_CODE,
    ENGINE,
    BAND;

    String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
