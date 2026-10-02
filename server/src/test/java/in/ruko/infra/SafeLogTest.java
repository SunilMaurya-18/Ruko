package in.ruko.infra;

import static in.ruko.infra.SafeLog.duration;
import static in.ruko.infra.SafeLog.errorType;
import static in.ruko.infra.SafeLog.id;
import static in.ruko.infra.SafeLog.num;
import static in.ruko.infra.SafeLog.tag;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SafeLogTest {

    @Test
    void rendersKeyValuePairs() {
        String line = SafeLog.render(LogEvent.REQUEST_REJECTED,
                num(LogKey.STATUS, 400), duration(LogKey.DURATION_MS, Duration.ofMillis(12)),
                tag(LogKey.PROBLEM, LogEvent.RATE_LIMITED), id(LogKey.SIGNAL_ID, "C3"));

        assertThat(line).isEqualTo("event=REQUEST_REJECTED status=400 duration_ms=12 problem=rate_limited signal_id=C3");
    }

    @Test
    void redactsAnythingThatIsNotAnIdentifier() {
        assertThat(SafeLog.render(LogEvent.REQUEST_REJECTED, id(LogKey.SIGNAL_ID, "pay to name@okaxis")))
                .endsWith("signal_id=[redacted]");
        assertThat(SafeLog.render(LogEvent.REQUEST_REJECTED, id(LogKey.SIGNAL_ID, null)))
                .endsWith("signal_id=[redacted]");
    }

    @Test
    void logsOnlyTheExceptionType() {
        String line = SafeLog.render(LogEvent.UNHANDLED_ERROR, errorType(new IllegalStateException("secret text")));

        assertThat(line).isEqualTo("event=UNHANDLED_ERROR error_type=IllegalStateException");
    }

    @Test
    void loggingMethodsTakeNoFreeText() {
        Arrays.stream(SafeLog.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.getName().equals("id"))
                .map(Method::getParameterTypes)
                .forEach(types -> assertThat(types).doesNotContain(String.class, CharSequence.class, Object.class));
    }
}
