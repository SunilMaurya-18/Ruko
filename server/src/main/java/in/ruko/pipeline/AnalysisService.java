package in.ruko.pipeline;

import static in.ruko.infra.SafeLog.duration;
import static in.ruko.infra.SafeLog.num;
import static in.ruko.infra.SafeLog.tag;

import in.ruko.api.dto.AnalyzeRequest;
import in.ruko.api.dto.AnalyzeResponse;
import in.ruko.explain.AnalogyCatalog;
import in.ruko.explain.Assist;
import in.ruko.explain.ExplainInput;
import in.ruko.explain.ExplainerPort;
import in.ruko.explain.LlmAssist;
import in.ruko.extract.EntityExtractor;
import in.ruko.guardrail.FallbackTemplates;
import in.ruko.guardrail.GuardrailLinter;
import in.ruko.guardrail.LintCode;
import in.ruko.guardrail.LintContext;
import in.ruko.guardrail.LintResult;
import in.ruko.infra.LogEvent;
import in.ruko.infra.LogKey;
import in.ruko.infra.RukoMetrics;
import in.ruko.infra.SafeLog;
import in.ruko.infra.config.AnalyzeProps;
import in.ruko.rules.Band;
import in.ruko.rules.BandCalculator;
import in.ruko.rules.ContentClass;
import in.ruko.rules.ContentClassifier;
import in.ruko.rules.RuleText;
import in.ruko.rules.Severity;
import in.ruko.rules.SignalEngine;
import in.ruko.rules.SignalHit;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * TRD §3 pipeline: guard, normalise, mask, extract, signals, optional LLM tags (additive), band, content class,
 * analogy, compose, lint. Rules alone decide band and class. Fails closed: the composed draft, else the pure template
 * draft, else the static generic response.
 */
@Service
public class AnalysisService {

    private static final SafeLog LOG = SafeLog.of(AnalysisService.class);
    private static final double MIN_LETTER_RATIO = 0.5;

    private final InputGuard guard;
    private final TextNormalizer normalizer;
    private final PiiMasker masker;
    private final EntityExtractor extractor;
    private final SignalEngine engine;
    private final ContentClassifier classifier;
    private final AnalogyCatalog analogies;
    private final LlmAssist llm;
    private final ExplainerPort explainer;
    private final GuardrailLinter linter;
    private final RukoMetrics metrics;
    private final AnalyzeProps props;

    public AnalysisService(InputGuard guard, TextNormalizer normalizer, PiiMasker masker, EntityExtractor extractor,
                           SignalEngine engine, ContentClassifier classifier, AnalogyCatalog analogies, LlmAssist llm,
                           ExplainerPort explainer, GuardrailLinter linter, RukoMetrics metrics, AnalyzeProps props) {
        this.guard = guard;
        this.normalizer = normalizer;
        this.masker = masker;
        this.extractor = extractor;
        this.engine = engine;
        this.classifier = classifier;
        this.analogies = analogies;
        this.llm = llm;
        this.explainer = explainer;
        this.linter = linter;
        this.metrics = metrics;
        this.props = props;
    }

    public AnalysisContext prepare(AnalyzeRequest request) {
        guard.check(request.text());
        MappedText normalized = normalizer.normalize(request.text());
        MappedText masked = masker.mask(normalized);
        EntityExtractor.Extraction extraction = extractor.extractWithSpans(masked.value());
        boolean unreadable = unreadable(masked.value(), request.ocrConfidence());
        return new AnalysisContext(normalized, masked, extraction.entities(), extraction.matches(),
                request.lang(), request.source(), unreadable);
    }

    public AnalyzeResponse analyze(AnalyzeRequest request) {
        long started = System.nanoTime();
        AnalysisContext ctx = prepare(request);
        RuleText text = RuleText.of(ctx);

        List<SignalHit> ruleHits = engine.evaluate(text);
        Assist assist = ctx.unreadable() ? Assist.EMPTY : llm.assist(text, ctx.lang(), ruleHits);
        List<SignalHit> hits = engine.merge(text, ruleHits, assist.tags());
        Band band = BandCalculator.band(hits, ctx.unreadable());
        ContentClass contentClass = ctx.unreadable() ? ContentClass.UNKNOWN
                : classifier.classify(text, withoutModelEducation(ruleHits, hits));
        String analogyKey = ctx.unreadable() ? null : analogies.select(text, hits).orElse(null);

        ExplainInput input = new ExplainInput(ctx.lang(), ctx.entities(), hits, band, contentClass, analogyKey,
                hits.size() > ruleHits.size());
        LintContext lintContext = new LintContext(ctx.lang(), text.quote(0, text.length()), band, contentClass,
                analogyKey, severities(hits));

        AnalyzeResponse response = explainer.compose(input, assist);
        LintResult lint = check(response, lintContext);
        if (!lint.ok() && !assist.cards().isEmpty()) {
            response = explainer.compose(input);
            lint = check(response, lintContext);
        }
        if (!lint.ok()) {
            response = FallbackTemplates.generic(ctx.lang(), ctx.entities(), band, contentClass, response.counts());
        }

        Duration took = Duration.ofNanos(System.nanoTime() - started);
        metrics.analyzed(band, response.engine(), took);
        LOG.info(LogEvent.ANALYZED, tag(LogKey.SOURCE, ctx.source()), tag(LogKey.BAND, band),
                tag(LogKey.ENGINE, response.engine()), num(LogKey.COUNT, response.signals().size()),
                duration(LogKey.DURATION_MS, took));
        return response;
    }

    private LintResult check(AnalyzeResponse response, LintContext ctx) {
        LintResult lint = linter.lint(response, ctx);
        for (LintCode code : lint.codes()) {
            metrics.lintFailed(code);
            LOG.warn(LogEvent.LINT_FAILED, tag(LogKey.LINT_CODE, code), tag(LogKey.ENGINE, response.engine()));
        }
        return lint;
    }

    /** A model may show R2 as reassurance, but only the rules can call a message education. */
    private static List<SignalHit> withoutModelEducation(List<SignalHit> ruleHits, List<SignalHit> hits) {
        return hits.stream()
                .filter(hit -> ruleHits.contains(hit) || !ContentClassifier.EDUCATION_SIGNAL.equals(hit.id()))
                .toList();
    }

    private static Map<String, Severity> severities(List<SignalHit> hits) {
        Map<String, Severity> severities = new LinkedHashMap<>();
        hits.forEach(hit -> severities.putIfAbsent(hit.id(), hit.severity()));
        return severities;
    }

    boolean unreadable(String maskedText, Double ocrConfidence) {
        if (ocrConfidence != null && ocrConfidence < props.minOcrConfidence()) {
            return true;
        }
        int letters = 0;
        int visible = 0;
        for (int i = 0; i < maskedText.length(); ) {
            int codePoint = maskedText.codePointAt(i);
            i += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                continue;
            }
            visible++;
            int type = Character.getType(codePoint);
            if (Character.isLetter(codePoint) || type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK) {
                letters++;
            }
        }
        return maskedText.strip().codePointCount(0, maskedText.strip().length()) < props.minChars()
                || letters < visible * MIN_LETTER_RATIO;
    }
}
