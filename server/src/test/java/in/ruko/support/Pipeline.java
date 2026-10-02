package in.ruko.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.ruko.api.dto.AnalyzeRequest;
import in.ruko.content.I18nBundle;
import in.ruko.explain.AnalogyCatalog;
import in.ruko.extract.EntityExtractor;
import in.ruko.extract.patterns.SharedPatterns;
import in.ruko.infra.config.AnalyzeProps;
import in.ruko.infra.config.FeatureFlags;
import in.ruko.pipeline.AnalysisService;
import in.ruko.pipeline.InputGuard;
import in.ruko.pipeline.Lang;
import in.ruko.pipeline.PiiMasker;
import in.ruko.pipeline.Source;
import in.ruko.pipeline.TextNormalizer;
import in.ruko.rules.ContentClassifier;
import in.ruko.rules.RuleLoader;
import in.ruko.rules.SignalEngine;
import in.ruko.snapshot.SebiSnapshotIndex;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The analyze pipeline without Spring, built from the same shared files the server loads. */
public final class Pipeline {

    public static final AnalyzeProps PROPS = new AnalyzeProps(4000, 20, 0.6, 65536);
    public static final I18nBundle I18N = new I18nBundle();
    public static final RuleLoader RULES = new RuleLoader(I18N);
    public static final SharedPatterns PATTERNS = new SharedPatterns();

    private Pipeline() {
    }

    public static SebiSnapshotIndex shippedSnapshot() {
        return new SebiSnapshotIndex(new FeatureFlags(false, true, false, true));
    }

    public static AnalysisService service() {
        return service(shippedSnapshot());
    }

    public static AnalysisService service(SebiSnapshotIndex snapshot) {
        return new AnalysisService(new InputGuard(PROPS), new TextNormalizer(), new PiiMasker(PATTERNS),
                new EntityExtractor(PATTERNS), new SignalEngine(RULES, snapshot), new ContentClassifier(RULES),
                new AnalogyCatalog(RULES), RULES, I18N, snapshot, PROPS);
    }

    public record Fixture(String id, String text, Lang lang, Source source, Double ocrConfidence, JsonNode expected) {

        public AnalyzeRequest request() {
            return new AnalyzeRequest(text, lang, source, ocrConfidence);
        }

        public List<String> expectedSignals() {
            List<String> ids = new ArrayList<>();
            expected.path("signals").forEach(id -> ids.add(id.asText()));
            return ids;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    public static List<Fixture> fixtures() {
        try (InputStream in = Pipeline.class.getClassLoader().getResourceAsStream("fixtures/fixtures.v0.json")) {
            List<Fixture> fixtures = new ArrayList<>();
            for (JsonNode node : new ObjectMapper().readTree(in)) {
                fixtures.add(new Fixture(node.path("id").asText(), node.path("text").asText(),
                        Lang.valueOf(node.path("lang").asText().toUpperCase(Locale.ROOT)),
                        Source.valueOf(node.path("source").asText().toUpperCase(Locale.ROOT)),
                        node.has("ocr_confidence") ? node.path("ocr_confidence").asDouble() : null,
                        node.path("expected")));
            }
            return fixtures;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
