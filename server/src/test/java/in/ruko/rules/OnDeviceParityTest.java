package in.ruko.rules;

import static org.assertj.core.api.Assertions.assertThat;

import in.ruko.api.dto.AnalyzeResponse;
import in.ruko.pipeline.AnalysisService;
import in.ruko.support.Pipeline;
import in.ruko.support.Pipeline.Fixture;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The parity contract both engines run over {@code shared/fixtures/fixtures.v0.json}: same band, same content
 * class, same rule-detectable signal ids. This is the server side. The PWA side
 * ({@code pwa/src/engine/parity.test.js}) is pending until the on-device engine is ported.
 */
class OnDeviceParityTest {

    private static final AnalysisService SERVICE = Pipeline.service();
    private static final Set<String> LLM_ONLY = Pipeline.RULES.ruleSet().rules().stream()
            .filter(rule -> rule.type() == RuleType.LLM_TAG).map(SignalRule::id).collect(Collectors.toSet());

    static List<Fixture> fixtures() {
        return Pipeline.fixtures();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void serverEngineMeetsTheSharedContract(Fixture fixture) {
        AnalyzeResponse response = SERVICE.analyze(fixture.request());

        assertThat(response.band().wire()).isEqualTo(fixture.expected().path("band").asText());
        assertThat(response.contentClass().wire()).isEqualTo(fixture.expected().path("content_class").asText());
        Set<String> ids = Stream.of(
                        response.signals().stream().map(AnalyzeResponse.Signal::id),
                        response.unverified().stream().map(AnalyzeResponse.Unverified::id),
                        response.reassuring().stream().map(AnalyzeResponse.Reassuring::id))
                .flatMap(s -> s).collect(Collectors.toCollection(TreeSet::new));
        assertThat(ids).isEqualTo(fixture.expectedSignals().stream()
                .filter(id -> !LLM_ONLY.contains(id)).collect(Collectors.toCollection(TreeSet::new)));
        assertThat(response.footerKey()).isEqualTo(AnalyzeResponse.FOOTER_KEY);
        assertThat(response.counts().redFlags()).isEqualTo(response.signals().size());
    }
}
