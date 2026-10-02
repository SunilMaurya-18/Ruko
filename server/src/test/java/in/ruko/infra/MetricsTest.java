package in.ruko.infra;

import static org.assertj.core.api.Assertions.assertThat;

import in.ruko.api.dto.AnalyzeRequest;
import in.ruko.pipeline.Lang;
import in.ruko.pipeline.Source;
import in.ruko.support.Pipeline;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MetricsTest {

    @Test
    void analyzeIsCountedByBandAndTimedWithoutContentTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RukoMetrics metrics = new RukoMetrics(registry);
        var service = Pipeline.service(Pipeline.shippedSnapshot(),
                Pipeline.llm(prompt -> "{}", Pipeline.LLM_OFF, metrics), metrics);

        service.analyze(new AnalyzeRequest("Guaranteed 5% daily returns! Pay ₹4999 to vipprofits@okaxis now.",
                Lang.EN, Source.SHARE, null));

        assertThat(registry.counter("ruko.analyze", "band", "high_concern").count()).isEqualTo(1);
        assertThat(registry.find("ruko.analyze.latency").tag("engine", "template").timer().count()).isEqualTo(1);
        assertThat(registry.counter("ruko.llm.fallback").count()).isZero();
        assertThat(registry.find("ruko.lint.fail").counters()).isEmpty();

        Set<String> tagKeys = registry.getMeters().stream().map(Meter::getId)
                .flatMap(id -> id.getTags().stream()).map(Tag::getKey).collect(Collectors.toSet());
        assertThat(tagKeys).isSubsetOf("band", "engine", "code");
    }
}
