package in.ruko.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.ruko.api.dto.AnalyzeRequest;
import in.ruko.api.dto.AnalyzeResponse;
import in.ruko.explain.ExplainInput;
import in.ruko.guardrail.LintContext;
import in.ruko.pipeline.AnalysisContext;
import in.ruko.rules.Band;
import in.ruko.rules.BandCalculator;
import in.ruko.rules.ContentClass;
import in.ruko.rules.RuleText;
import in.ruko.rules.Severity;
import in.ruko.rules.SignalHit;
import java.util.LinkedHashMap;
import java.util.Map;
import in.ruko.content.I18nBundle;
import in.ruko.explain.AnalogyCatalog;
import in.ruko.explain.LlmAssist;
import in.ruko.explain.LlmPort;
import in.ruko.explain.LlmPrompt;
import in.ruko.explain.PromptFactory;
import in.ruko.explain.SchemaGuard;
import in.ruko.explain.TemplateExplainer;
import in.ruko.extract.EntityExtractor;
import in.ruko.extract.patterns.SharedPatterns;
import in.ruko.guardrail.GuardrailLinter;
import in.ruko.guardrail.OutboundLinkPolicy;
import in.ruko.guardrail.TickerSnapshot;
import in.ruko.infra.RukoMetrics;
import in.ruko.infra.config.AnalyzeProps;
import in.ruko.infra.config.FeatureFlags;
import in.ruko.infra.config.LinksProps;
import in.ruko.infra.config.LlmProps;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
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
    public static final OutboundLinkPolicy LINKS = new OutboundLinkPolicy(
            new LinksProps(List.of("siportal.sebi.gov.in", "scores.sebi.gov.in", "cybercrime.gov.in")));
    public static final TickerSnapshot TICKERS = new TickerSnapshot();
    public static final GuardrailLinter LINTER = new GuardrailLinter(I18N, LINKS, TICKERS);
    public static final LlmProps LLM_OFF = new LlmProps(false, Duration.ofSeconds(3), "", "", "");

    private Pipeline() {
    }

    public static SebiSnapshotIndex shippedSnapshot() {
        return new SebiSnapshotIndex(new FeatureFlags(false, true, false, true));
    }

    public static AnalysisService service() {
        return service(shippedSnapshot());
    }

    public static AnalysisService service(SebiSnapshotIndex snapshot) {
        RukoMetrics metrics = new RukoMetrics(new SimpleMeterRegistry());
        return service(snapshot, llm(prompt -> "{}", LLM_OFF, metrics), metrics);
    }

    public static AnalysisService service(SebiSnapshotIndex snapshot, LlmAssist llm, RukoMetrics metrics) {
        return new AnalysisService(new InputGuard(PROPS), new TextNormalizer(), new PiiMasker(PATTERNS),
                new EntityExtractor(PATTERNS), new SignalEngine(RULES, snapshot), new ContentClassifier(RULES),
                new AnalogyCatalog(RULES), llm, explainer(snapshot), LINTER, metrics, PROPS);
    }

    public static TemplateExplainer explainer(SebiSnapshotIndex snapshot) {
        return new TemplateExplainer(RULES, I18N, snapshot);
    }

    /** A pure template draft and the rule result it must be linted against. */
    public record Drafted(AnalyzeResponse response, LintContext context) {
    }

    public static Drafted drafted(AnalyzeRequest request, SebiSnapshotIndex snapshot) {
        AnalysisContext ctx = service(snapshot).prepare(request);
        RuleText text = RuleText.of(ctx);
        List<SignalHit> hits = new SignalEngine(RULES, snapshot).evaluate(text);
        Band band = BandCalculator.band(hits, ctx.unreadable());
        ContentClass contentClass = ctx.unreadable() ? ContentClass.UNKNOWN : new ContentClassifier(RULES).classify(text, hits);
        String analogyKey = ctx.unreadable() ? null : new AnalogyCatalog(RULES).select(text, hits).orElse(null);
        AnalyzeResponse response = explainer(snapshot).compose(
                new ExplainInput(request.lang(), ctx.entities(), hits, band, contentClass, analogyKey, false));
        Map<String, Severity> severities = new LinkedHashMap<>();
        hits.forEach(hit -> severities.putIfAbsent(hit.id(), hit.severity()));
        return new Drafted(response, new LintContext(request.lang(), text.quote(0, text.length()), band,
                contentClass, analogyKey, severities));
    }

    public static SebiSnapshotIndex datedSnapshot() {
        return SebiSnapshotIndex.parse("{\"snapshot_date\": \"2026-09-30\", \"registration_numbers\": [\"INH000000001\"]}");
    }

    /** A model stub: gets the prompt, returns the reply text (or throws). */
    @FunctionalInterface
    public interface Model {
        String reply(LlmPrompt prompt) throws Exception;
    }

    public static LlmProps llmOn(Duration timeout) {
        return new LlmProps(true, timeout, "https://llm.test/v1", "test-key", "test-model");
    }

    public static LlmAssist llm(Model model, LlmProps props, RukoMetrics metrics) {
        LlmPort port = (prompt, timeout) -> model.reply(prompt);
        return new LlmAssist(port, new PromptFactory(RULES, I18N), new SchemaGuard(), props, RULES,
                CircuitBreaker.ofDefaults("test"), metrics);
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
