package in.ruko.extract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.ruko.extract.patterns.SharedPatterns;
import in.ruko.pipeline.PiiMasker;
import in.ruko.pipeline.TextNormalizer;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Precision and recall per entity type over the labelled fixtures, through the server pipeline
 * (normalise, mask, extract). Phrase types count as found when one value contains the other.
 */
class EntityExtractionFixtureTest {

    private static final double GATE = 0.95;
    private static final Set<String> PHRASE_TYPES = Set.of("return_claims", "qr_phrases");

    private final SharedPatterns patterns = new SharedPatterns();
    private final TextNormalizer normalizer = new TextNormalizer();
    private final PiiMasker masker = new PiiMasker(patterns);
    private final EntityExtractor extractor = new EntityExtractor(patterns);

    private static final class Score {
        int tp;
        int fp;
        int fn;

        double precision() {
            return tp + fp == 0 ? 1 : (double) tp / (tp + fp);
        }

        double recall() {
            return tp + fn == 0 ? 1 : (double) tp / (tp + fn);
        }
    }

    @Test
    void extractionMeetsTheGateOnEveryEntityType() throws IOException {
        Map<String, Score> scores = new LinkedHashMap<>();
        Entities.TYPE_IDS.forEach(type -> scores.put(type, new Score()));
        scores.put("phone_count", new Score());
        List<String> misses = new ArrayList<>();

        for (JsonNode fixture : fixtures()) {
            String id = fixture.path("id").asText();
            JsonNode labels = fixture.path("expected").path("entities");
            Entities actual = extractor.extract(masker.mask(normalizer.normalize(fixture.path("text").asText())).value());

            for (String type : Entities.TYPE_IDS) {
                List<String> expected = new ArrayList<>();
                labels.path(type).forEach(value -> expected.add(value.asText()));
                List<String> got = actual.byType(type);
                boolean phrase = PHRASE_TYPES.contains(type);
                Score score = scores.get(type);
                for (String value : got) {
                    if (expected.stream().anyMatch(label -> matches(phrase, value, label))) {
                        score.tp++;
                    } else {
                        score.fp++;
                        misses.add(id + " " + type + " unexpected: " + value);
                    }
                }
                for (String label : expected) {
                    if (got.stream().noneMatch(value -> matches(phrase, value, label))) {
                        score.fn++;
                        misses.add(id + " " + type + " missed: " + label);
                    }
                }
            }

            int expectedPhones = labels.path("phone_count").asInt(0);
            Score phones = scores.get("phone_count");
            phones.tp += Math.min(expectedPhones, actual.phoneCount());
            phones.fp += Math.max(0, actual.phoneCount() - expectedPhones);
            phones.fn += Math.max(0, expectedPhones - actual.phoneCount());
            if (expectedPhones != actual.phoneCount()) {
                misses.add(id + " phone_count expected " + expectedPhones + " got " + actual.phoneCount());
            }
        }

        StringBuilder report = new StringBuilder("\nentity type      precision  recall   tp  fp  fn\n");
        scores.forEach((type, s) -> report.append(String.format("%-16s %9.3f %7.3f %4d %3d %3d%n",
                type, s.precision(), s.recall(), s.tp, s.fp, s.fn)));
        misses.forEach(miss -> report.append("  ").append(miss).append('\n'));
        System.out.println(report);

        scores.forEach((type, s) -> {
            assertThat(s.precision()).as(type + " precision" + report).isGreaterThanOrEqualTo(GATE);
            assertThat(s.recall()).as(type + " recall" + report).isGreaterThanOrEqualTo(GATE);
        });
    }

    private static boolean matches(boolean phrase, String value, String label) {
        return phrase ? value.contains(label) || label.contains(value) : value.equals(label);
    }

    private static JsonNode fixtures() throws IOException {
        try (InputStream in = EntityExtractionFixtureTest.class.getClassLoader()
                .getResourceAsStream("fixtures/fixtures.v0.json")) {
            return new ObjectMapper().readTree(in);
        }
    }
}
