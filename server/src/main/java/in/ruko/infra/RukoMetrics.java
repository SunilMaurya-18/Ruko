package in.ruko.infra;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * TRD §8 metrics. Tags are enum names only, never content. Prometheus names: {@code ruko_analyze_total{band}},
 * {@code ruko_lint_fail_total{code}}, {@code ruko_llm_fallback_total}, {@code ruko_tts_fallback_total}, and the
 * latency timers.
 */
@Component
public class RukoMetrics {

    private final MeterRegistry registry;
    private final Counter llmFallback;
    private final Timer llmLatency;
    private final Counter ttsFallback;
    private final Timer ttsLatency;

    public RukoMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.llmFallback = Counter.builder("ruko.llm.fallback").register(registry);
        this.llmLatency = Timer.builder("ruko.llm.latency").register(registry);
        this.ttsFallback = Counter.builder("ruko.tts.fallback").register(registry);
        this.ttsLatency = Timer.builder("ruko.tts.latency").register(registry);
    }

    public void analyzed(Enum<?> band, Enum<?> engine, Duration took) {
        registry.counter("ruko.analyze", "band", label(band)).increment();
        Timer.builder("ruko.analyze.latency").tag("engine", label(engine)).register(registry).record(took);
    }

    public void lintFailed(Enum<?> code) {
        registry.counter("ruko.lint.fail", "code", code.name()).increment();
    }

    public void llmFallback() {
        llmFallback.increment();
    }

    public void llmTook(Duration took) {
        llmLatency.record(took);
    }

    public void ttsFallback() {
        ttsFallback.increment();
    }

    public void ttsTook(Duration took) {
        ttsLatency.record(took);
    }

    private static String label(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
