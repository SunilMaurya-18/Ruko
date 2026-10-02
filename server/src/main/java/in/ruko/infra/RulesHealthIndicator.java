package in.ruko.infra;

import static in.ruko.infra.SafeLog.errorType;
import static in.ruko.infra.SafeLog.id;

import in.ruko.rules.RulesReadiness;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * The {@code rules} contributor of the readiness group ({@code /actuator/health/readiness}): down when the shipped
 * rules no longer compile or a readiness probe comes out differently. A pass is reused for a while, since the rules
 * cannot change inside a running jar and the host polls often.
 */
@Component
public class RulesHealthIndicator implements HealthIndicator {

    private static final SafeLog LOG = SafeLog.of(RulesHealthIndicator.class);
    static final Duration REUSE_PASS = Duration.ofMinutes(10);

    private final RulesReadiness readiness;
    private volatile long passedAt;
    private volatile boolean passed;

    public RulesHealthIndicator(RulesReadiness readiness) {
        this.readiness = readiness;
    }

    @Override
    public Health health() {
        if (passed && System.nanoTime() - passedAt < REUSE_PASS.toNanos()) {
            return Health.up().build();
        }
        try {
            List<String> failed = readiness.failures();
            if (failed.isEmpty()) {
                passedAt = System.nanoTime();
                passed = true;
                return Health.up().build();
            }
            failed.forEach(probe -> LOG.error(LogEvent.READINESS_FAILED, id(LogKey.PROBE, probe)));
            return Health.down().build();
        } catch (RuntimeException e) {
            LOG.error(LogEvent.READINESS_FAILED, errorType(e));
            return Health.down().build();
        }
    }
}
