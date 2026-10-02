package in.ruko.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Keeps the shared labelled messages well-formed and internally consistent with the TRD band rule. */
class FixtureFormatTest {

    private static final int MIN_FIXTURES = 80;
    private static final Map<String, Integer> MIN_PER_KIND = Map.of("scam", 40, "education", 25, "ambiguous", 15);

    private static final Map<String, String> SEVERITY = Map.ofEntries(
            Map.entry("C1", "critical"), Map.entry("C2", "critical"), Map.entry("C3", "critical"),
            Map.entry("C4", "critical"), Map.entry("C15", "critical"), Map.entry("C16", "critical"),
            Map.entry("C17", "critical"),
            Map.entry("S5", "strong"), Map.entry("S6", "strong"), Map.entry("S7", "strong"),
            Map.entry("S8", "strong"), Map.entry("S9", "strong"), Map.entry("S10", "strong"),
            Map.entry("S19", "strong"),
            Map.entry("M11", "moderate"), Map.entry("M12", "moderate"), Map.entry("M13", "moderate"),
            Map.entry("U14", "unverified"),
            Map.entry("R1", "reassurance"), Map.entry("R2", "reassurance"));

    private static JsonNode fixtures;

    @BeforeAll
    static void load() throws IOException {
        fixtures = read("fixtures/fixtures.v0.json");
    }

    @Test
    void matchesSchema() throws IOException {
        JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(read("schemas/fixtures.v0.json"));

        Set<ValidationMessage> errors = schema.validate(fixtures);

        assertThat(errors).isEmpty();
    }

    @Test
    void hasEnoughDistinctFixturesAcrossKindsAndVarieties() {
        assertThat(fixtures.size()).isGreaterThanOrEqualTo(MIN_FIXTURES);
        assertThat(values("id")).hasSize(fixtures.size());
        assertThat(values("kind")).containsExactlyInAnyOrder("scam", "education", "ambiguous");
        assertThat(values("variety")).containsExactlyInAnyOrder("hindi", "english", "hinglish");
        Map<String, Long> perKind = StreamSupport.stream(fixtures.spliterator(), false)
                .collect(Collectors.groupingBy(fixture -> fixture.path("kind").asText(), Collectors.counting()));
        MIN_PER_KIND.forEach((kind, min) -> assertThat(perKind.get(kind)).as(kind).isGreaterThanOrEqualTo(min.longValue()));
    }

    @Test
    void labelsUseKnownSignalsAndAgreeWithTheBandRule() {
        for (JsonNode fixture : fixtures) {
            String id = fixture.path("id").asText();
            JsonNode expected = fixture.path("expected");
            Set<String> signals = new HashSet<>();
            expected.path("signals").forEach(signal -> signals.add(signal.asText()));

            assertThat(SEVERITY.keySet()).as(id).containsAll(signals);
            String band = expected.path("band").asText();
            if (!band.equals("not_enough_to_judge")) {
                assertThat(band).as(id).isEqualTo(band(signals));
            }
            if (expected.path("content_class").asText().equals("education")) {
                assertThat(signals).as(id).contains("R2");
                assertThat(signals).as(id).noneMatch(s -> SEVERITY.get(s).matches("critical|strong"));
            }
        }
    }

    private static String band(Set<String> signals) {
        Map<String, Long> n = signals.stream().collect(Collectors.groupingBy(SEVERITY::get, Collectors.counting()));
        long c = n.getOrDefault("critical", 0L);
        long s = n.getOrDefault("strong", 0L);
        long m = n.getOrDefault("moderate", 0L);
        if (c >= 1 || s >= 2) {
            return "high_concern";
        }
        if (s == 1 || m >= 2) {
            return "some_concern";
        }
        return "few_flags_still_verify";
    }

    private static Set<String> values(String field) {
        return StreamSupport.stream(fixtures.spliterator(), false)
                .map(fixture -> fixture.path(field).asText())
                .collect(Collectors.toSet());
    }

    private static JsonNode read(String resource) throws IOException {
        try (InputStream in = FixtureFormatTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            return new ObjectMapper().readTree(in);
        }
    }
}
