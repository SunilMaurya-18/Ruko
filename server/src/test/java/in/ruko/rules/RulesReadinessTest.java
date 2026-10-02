package in.ruko.rules;

import static org.assertj.core.api.Assertions.assertThat;

import in.ruko.rules.RulesReadiness.Expected;
import in.ruko.rules.RulesReadiness.Probe;
import in.ruko.support.Pipeline;
import in.ruko.support.Pipeline.Fixture;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RulesReadinessTest {

    private static final Map<String, Fixture> FIXTURES = Pipeline.fixtures().stream()
            .collect(Collectors.toMap(Fixture::id, Function.identity()));

    @Test
    void probesAreLabelledFixturesCopiedVerbatim() {
        List<Probe> probes = RulesReadiness.probes();

        assertThat(probes).hasSizeGreaterThanOrEqualTo(RulesReadiness.MIN_PROBES);
        for (Probe probe : probes) {
            Fixture fixture = FIXTURES.get(probe.id());
            assertThat(fixture).as(probe.id() + " is in fixtures.v0.json").isNotNull();
            assertThat(fixture.hasKnownGap()).as(probe.id() + " has no known gap").isFalse();
            assertThat(probe.text()).as(probe.id()).isEqualTo(fixture.text());
            assertThat(probe.lang()).as(probe.id()).isEqualTo(fixture.lang().wire());
            assertThat(probe.source()).as(probe.id()).isEqualToIgnoringCase(fixture.source().name());
            assertThat(probe.expected().band()).as(probe.id()).isEqualTo(fixture.expectedBand());
            assertThat(probe.expected().contentClass()).as(probe.id()).isEqualTo(fixture.expectedClass());
            assertThat(probe.expected().signals()).as(probe.id())
                    .containsExactlyInAnyOrderElementsOf(fixture.expectedSignals());
        }
    }

    @Test
    void probesCoverBothLanguagesAScamAtEachConcernLevelAndEducation() {
        List<Probe> probes = RulesReadiness.probes();

        assertThat(probes).extracting(Probe::lang).contains("hi", "en");
        assertThat(probes).extracting(probe -> probe.expected().band()).contains("high_concern", "some_concern");
        assertThat(probes).extracting(probe -> probe.expected().contentClass()).contains("promotion", "education");
    }

    @Test
    void shippedRulesPassEveryProbe() {
        assertThat(new RulesReadiness(Pipeline.I18N, Pipeline.service()).failures()).isEmpty();
    }

    @Test
    void aProbeThatComesOutDifferentlyIsReportedById() {
        Probe real = RulesReadiness.probes().getFirst();
        Probe wrongBand = new Probe("wrong-band", real.lang(), real.source(), real.text(),
                new Expected("few_flags_still_verify", real.expected().contentClass(), real.expected().signals()));
        Probe wrongSignals = new Probe("wrong-signals", real.lang(), real.source(), real.text(),
                new Expected(real.expected().band(), real.expected().contentClass(), List.of("C1")));

        assertThat(new RulesReadiness(Pipeline.I18N, Pipeline.service(), List.of(real, wrongBand, wrongSignals))
                .failures()).containsExactly("wrong-band", "wrong-signals");
    }
}
