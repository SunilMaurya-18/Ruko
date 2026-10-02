package in.ruko.rules;

import static org.assertj.core.api.Assertions.assertThat;

import in.ruko.api.dto.AnalyzeResponse;
import in.ruko.pipeline.AnalysisService;
import in.ruko.support.Pipeline;
import in.ruko.support.Pipeline.Fixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every fixture through the template engine: the signal ids fired (minus LLM-only tags, which need the model)
 * must equal the labels exactly, and band and class must match, except on fixtures with a {@code known_gap} note,
 * which must still disagree. Prints per-signal precision and recall over all fixtures, gaps included.
 */
class SignalFixtureTest {

    private final AnalysisService service = Pipeline.service();

    @Test
    void rulesReproduceEveryFixtureLabel() {
        Set<String> llmOnly = Pipeline.RULES.ruleSet().rules().stream()
                .filter(rule -> rule.type() == RuleType.LLM_TAG).map(SignalRule::id).collect(Collectors.toSet());
        Map<String, int[]> scores = new TreeMap<>();
        List<String> misses = new ArrayList<>();

        for (Fixture fixture : Pipeline.fixtures()) {
            AnalyzeResponse response = service.analyze(fixture.request());
            Set<String> got = ids(response);
            Set<String> want = fixture.expectedSignals().stream()
                    .filter(id -> !llmOnly.contains(id)).collect(Collectors.toCollection(TreeSet::new));
            for (String id : got) {
                scores.computeIfAbsent(id, k -> new int[3])[want.contains(id) ? 0 : 1]++;
            }
            for (String id : want) {
                if (!got.contains(id)) {
                    scores.computeIfAbsent(id, k -> new int[3])[2]++;
                }
            }
            String band = response.band().wire();
            String contentClass = response.contentClass().wire();
            boolean agrees = got.equals(want) && band.equals(fixture.expectedBand())
                    && contentClass.equals(fixture.expectedClass());
            if (!agrees && !fixture.hasKnownGap()) {
                misses.add(String.format("%s got %s %s %s, want %s %s %s", fixture.id(), got, band, contentClass,
                        want, fixture.expectedBand(), fixture.expectedClass()));
            }
            if (agrees && fixture.hasKnownGap()) {
                misses.add(fixture.id() + " now matches its labels: remove its known_gap note");
            }
        }

        StringBuilder report = new StringBuilder("\nsignal  precision  recall   tp  fp  fn\n");
        scores.forEach((id, s) -> report.append(String.format("%-7s %9.3f %7.3f %4d %3d %3d%n", id,
                s[0] + s[1] == 0 ? 1.0 : (double) s[0] / (s[0] + s[1]),
                s[0] + s[2] == 0 ? 1.0 : (double) s[0] / (s[0] + s[2]), s[0], s[1], s[2])));
        misses.forEach(miss -> report.append("  ").append(miss).append('\n'));
        System.out.println(report);

        assertThat(misses).as(report.toString()).isEmpty();
    }

    private static Set<String> ids(AnalyzeResponse response) {
        return Stream.of(
                        response.signals().stream().map(AnalyzeResponse.Signal::id),
                        response.unverified().stream().map(AnalyzeResponse.Unverified::id),
                        response.reassuring().stream().map(AnalyzeResponse.Reassuring::id))
                .flatMap(s -> s)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
