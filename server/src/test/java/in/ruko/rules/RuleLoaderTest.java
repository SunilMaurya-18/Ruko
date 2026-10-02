package in.ruko.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.ruko.support.Pipeline;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RuleLoaderTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static ObjectNode shipped() throws IOException {
        try (InputStream in = RuleLoaderTest.class.getClassLoader().getResourceAsStream(RuleLoader.RESOURCE)) {
            return (ObjectNode) JSON.readTree(in);
        }
    }

    private static ObjectNode signal(ObjectNode file, String id) {
        for (JsonNode signal : file.path("signals")) {
            if (signal.path("id").asText().equals(id)) {
                return (ObjectNode) signal;
            }
        }
        throw new IllegalArgumentException(id);
    }

    private static void assertAborts(Consumer<ObjectNode> mutation, String expectedProblem) throws IOException {
        ObjectNode file = shipped();
        mutation.accept(file);
        String json = file.toString();
        assertThatThrownBy(() -> RuleLoader.load(json, Pipeline.I18N))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(expectedProblem);
    }

    @Test
    void shippedCatalogueLoadsWithEveryTrdSignalAtItsSeverity() {
        Map<String, Severity> severities = Pipeline.RULES.ruleSet().rules().stream()
                .collect(Collectors.toMap(SignalRule::id, SignalRule::severity));
        assertThat(severities).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("C1", Severity.CRITICAL), Map.entry("C2", Severity.CRITICAL),
                Map.entry("C3", Severity.CRITICAL), Map.entry("C4", Severity.CRITICAL),
                Map.entry("C15", Severity.CRITICAL), Map.entry("C16", Severity.CRITICAL),
                Map.entry("C17", Severity.CRITICAL),
                Map.entry("S5", Severity.STRONG), Map.entry("S6", Severity.STRONG), Map.entry("S7", Severity.STRONG),
                Map.entry("S8", Severity.STRONG), Map.entry("S9", Severity.STRONG), Map.entry("S10", Severity.STRONG),
                Map.entry("S19", Severity.STRONG),
                Map.entry("M11", Severity.MODERATE), Map.entry("M12", Severity.MODERATE),
                Map.entry("M13", Severity.MODERATE),
                Map.entry("U14", Severity.UNVERIFIED),
                Map.entry("R1", Severity.REASSURANCE), Map.entry("R2", Severity.REASSURANCE)));
    }

    @Test
    void shippedCatalogueKeepsTheTrdSwitchesAndTags() {
        RuleSet rules = Pipeline.RULES.ruleSet();
        assertThat(rules.require("S10").enabled()).isFalse();
        assertThat(rules.require("S19").detector()).isEqualTo("snapshot");
        assertThat(rules.require("S19").textRule()).isFalse();
        assertThat(rules.require("U14").action()).isEqualTo("sebi_check");
        assertThat(rules.rules().stream().filter(SignalRule::llmTag).map(SignalRule::id))
                .containsExactlyInAnyOrder("C1", "S9", "M12", "R2");
        assertThat(rules.require("S5").type()).isEqualTo(RuleType.PREFIX_MAP);
    }

    @Test
    void invalidJsonAborts() {
        assertThatThrownBy(() -> RuleLoader.load("{\"version\": 0,", Pipeline.I18N))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not valid JSON");
    }

    @Test
    void unknownTopLevelKeyAborts() throws IOException {
        assertAborts(file -> file.put("weights", 2), "fails schemas/signals.v0.json");
    }

    @Test
    void unknownSignalKeyAborts() throws IOException {
        assertAborts(file -> signal(file, "C3").put("weight", 2), "fails schemas/signals.v0.json");
    }

    @Test
    void unknownRuleTypeAborts() throws IOException {
        assertAborts(file -> signal(file, "C3").put("type", "FUZZY"), "fails schemas/signals.v0.json");
    }

    @Test
    void unknownSeverityAborts() throws IOException {
        assertAborts(file -> signal(file, "C3").put("severity", "SEVERE"), "fails schemas/signals.v0.json");
    }

    @Test
    void missingRequiredFieldForTypeAborts() throws IOException {
        assertAborts(file -> signal(file, "C4").remove("patterns"), "fails schemas/signals.v0.json");
    }

    @Test
    void badRegexAborts() throws IOException {
        assertAborts(file -> ((ArrayNode) signal(file, "C4").path("patterns")).set(0, "(apk"), "invalid pattern");
        assertAborts(file -> ((ArrayNode) signal(file, "C4").path("patterns")).set(0, "([a-z"),
                "unclosed character class");
    }

    @Test
    void regexOutsideTheJavaScriptSubsetAborts() throws IOException {
        assertAborts(file -> ((ArrayNode) signal(file, "C4").path("patterns")).set(0, "\\bapk\\b"),
                "outside the Java/JavaScript regex subset");
        assertAborts(file -> ((ArrayNode) signal(file, "C4").path("patterns")).set(0, "apk.+link"),
                "unescaped '.'");
        assertAborts(file -> ((ArrayNode) signal(file, "C4").path("patterns")).set(0, "(?i)apk"),
                "group syntax");
    }

    @Test
    void duplicateIdAborts() throws IOException {
        assertAborts(file -> ((ArrayNode) file.path("signals")).add(signal(file, "C3").deepCopy()), "duplicate id C3");
    }

    @Test
    void missingCatalogueTextAborts() throws IOException {
        assertAborts(file -> signal(file, "C3").put("reason_key", "sig.C99.reason"),
                "needs catalogue text sig.C99.reason");
        assertAborts(file -> signal(file, "C3").put("analogy_key", "analogy.lottery"),
                "needs catalogue text analogy.lottery");
    }

    @Test
    void unknownSetOrMarkerAborts() throws IOException {
        assertAborts(file -> signal(file, "C3").putArray("sets").add("lottery_words"), "unknown set lottery_words");
        assertAborts(file -> ((ArrayNode) signal(file, "R2").path("unless").path("markers")).add("lottery"),
                "unknown marker lottery");
    }

    @Test
    void enablingARuleWhoseDetectorIsNotBuiltAborts() throws IOException {
        assertAborts(file -> signal(file, "S10").put("enabled", true), "no rdap detector");
    }
}
