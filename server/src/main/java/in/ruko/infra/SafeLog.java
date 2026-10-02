package in.ruko.infra;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The only logging facade in Ruko. It deliberately has no method that accepts free text: callers can log
 * an event, enums, numbers, durations, catalogue ids, and exception types. Request content cannot reach it
 * without first being turned into one of those.
 */
public final class SafeLog {

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,31}");
    private static final String REDACTED = "[redacted]";

    private final Logger delegate;

    private SafeLog(Class<?> owner) {
        this.delegate = LoggerFactory.getLogger(owner);
    }

    public static SafeLog of(Class<?> owner) {
        return new SafeLog(owner);
    }

    public void info(LogEvent event, Field... fields) {
        if (delegate.isInfoEnabled()) {
            delegate.info(render(event, fields));
        }
    }

    public void warn(LogEvent event, Field... fields) {
        if (delegate.isWarnEnabled()) {
            delegate.warn(render(event, fields));
        }
    }

    public void error(LogEvent event, Field... fields) {
        if (delegate.isErrorEnabled()) {
            delegate.error(render(event, fields));
        }
    }

    public static Field num(LogKey key, long value) {
        return new Field(key, Long.toString(value));
    }

    public static Field duration(LogKey key, Duration value) {
        return new Field(key, Long.toString(value.toMillis()));
    }

    public static Field tag(LogKey key, Enum<?> value) {
        return new Field(key, value.name().toLowerCase(Locale.ROOT));
    }

    /** Catalogue identifiers such as signal ids or lint codes; anything that does not look like one is redacted. */
    public static Field id(LogKey key, String value) {
        return new Field(key, value != null && SAFE_ID.matcher(value).matches() ? value : REDACTED);
    }

    /** Exception messages can quote input, so only the type is ever logged. */
    public static Field errorType(Throwable error) {
        return new Field(LogKey.ERROR_TYPE, error.getClass().getSimpleName());
    }

    static String render(LogEvent event, Field... fields) {
        var line = new StringBuilder("event=").append(event.name());
        for (Field field : fields) {
            line.append(' ').append(field.key.label()).append('=').append(field.value);
        }
        return line.toString();
    }

    public static final class Field {
        private final LogKey key;
        private final String value;

        private Field(LogKey key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
