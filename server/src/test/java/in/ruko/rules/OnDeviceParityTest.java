package in.ruko.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.ruko.api.dto.AnalyzeRequest;
import in.ruko.api.dto.AnalyzeResponse;
import in.ruko.pipeline.AnalysisService;
import in.ruko.pipeline.Lang;
import in.ruko.pipeline.Source;
import in.ruko.support.Pipeline;
import in.ruko.support.Pipeline.Fixture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The parity contract both engines run over {@code shared/fixtures/fixtures.v0.json}: same band, same content
 * class, same rule-detectable signal ids. This is the server side; it also keeps
 * {@code shared/fixtures/engine-golden.v0.json} (the full server response, LLM off, per fixture and per edge-case
 * probe in {@code engine-probes.v0.json}) current, which the
 * PWA's {@code src/engine/parity.test.js} compares the on-device engine against field by field.
 * Regenerate after a rule or catalogue change with {@code -Druko.updateGolden=true}.
 */
class OnDeviceParityTest {

    private static final AnalysisService SERVICE = Pipeline.service();
    private static final Set<String> LLM_ONLY = Pipeline.RULES.ruleSet().rules().stream()
            .filter(rule -> rule.type() == RuleType.LLM_TAG).map(SignalRule::id).collect(Collectors.toSet());
    private static final Path GOLDEN = Path.of("..", "shared", "fixtures", "engine-golden.v0.json");
    private static final Path PROBES = Path.of("..", "shared", "fixtures", "engine-probes.v0.json");
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

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

    /** Fixtures, then the edge-case probes in {@code engine-probes.v0.json} (some against a dated snapshot). */
    private static List<Map.Entry<String, AnalyzeResponse>> serverResponses() throws IOException {
        List<Map.Entry<String, AnalyzeResponse>> responses = new ArrayList<>();
        for (Fixture fixture : fixtures()) {
            responses.add(Map.entry(fixture.id(), SERVICE.analyze(fixture.request())));
        }
        AnalysisService dated = Pipeline.service(Pipeline.datedSnapshot());
        for (JsonNode probe : MAPPER.readTree(Files.readString(PROBES, StandardCharsets.UTF_8))) {
            AnalyzeRequest request = new AnalyzeRequest(probe.path("text").asText(),
                    Lang.valueOf(probe.path("lang").asText().toUpperCase(Locale.ROOT)),
                    Source.valueOf(probe.path("source").asText().toUpperCase(Locale.ROOT)),
                    probe.has("ocr_confidence") ? probe.path("ocr_confidence").asDouble() : null);
            AnalysisService service = "dated".equals(probe.path("snapshot").asText()) ? dated : SERVICE;
            responses.add(Map.entry(probe.path("id").asText(), service.analyze(request)));
        }
        return responses;
    }

    @Test
    void goldenFileIsTheServerEngineOutput() throws IOException {
        List<String> lines = new ArrayList<>();
        ArrayNode expected = MAPPER.createArrayNode();
        for (Map.Entry<String, AnalyzeResponse> response : serverResponses()) {
            ObjectNode entry = MAPPER.createObjectNode();
            entry.put("id", response.getKey());
            entry.set("response", MAPPER.valueToTree(response.getValue()));
            expected.add(entry);
            lines.add("  " + MAPPER.writeValueAsString(entry));
        }
        if (Boolean.getBoolean("ruko.updateGolden")) {
            Files.writeString(GOLDEN, "[\n" + String.join(",\n", lines) + "\n]\n", StandardCharsets.UTF_8);
        }
        JsonNode golden = MAPPER.readTree(Files.readString(GOLDEN, StandardCharsets.UTF_8));
        assertThat(golden)
                .as("%s is stale: run the server tests with -Druko.updateGolden=true and commit the file", GOLDEN)
                .isEqualTo(expected);
    }
}
