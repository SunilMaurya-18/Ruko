package in.ruko.pipeline;

import static in.ruko.infra.SafeLog.duration;
import static in.ruko.infra.SafeLog.num;
import static in.ruko.infra.SafeLog.tag;

import in.ruko.api.dto.AnalyzeRequest;
import in.ruko.api.dto.AnalyzeResponse;
import in.ruko.content.I18nBundle;
import in.ruko.explain.AnalogyCatalog;
import in.ruko.extract.EntityExtractor;
import in.ruko.infra.LogEvent;
import in.ruko.infra.LogKey;
import in.ruko.infra.SafeLog;
import in.ruko.infra.config.AnalyzeProps;
import in.ruko.rules.Band;
import in.ruko.rules.BandCalculator;
import in.ruko.rules.ContentClass;
import in.ruko.rules.ContentClassifier;
import in.ruko.rules.RuleLoader;
import in.ruko.rules.RuleSet;
import in.ruko.rules.RuleText;
import in.ruko.rules.Severity;
import in.ruko.rules.SignalEngine;
import in.ruko.rules.SignalHit;
import in.ruko.rules.SignalRule;
import in.ruko.snapshot.SebiSnapshotIndex;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * TRD §3 pipeline: guard, normalise, mask, extract, signals, band, content class, analogy. Rules alone decide the
 * band and the class. Cards stay empty until the explain layer lands.
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
    private final RuleSet rules;
    private final I18nBundle i18n;
    private final SebiSnapshotIndex snapshot;
    private final AnalyzeProps props;

    public AnalysisService(InputGuard guard, TextNormalizer normalizer, PiiMasker masker, EntityExtractor extractor,
                           SignalEngine engine, ContentClassifier classifier, AnalogyCatalog analogies,
                           RuleLoader rules, I18nBundle i18n, SebiSnapshotIndex snapshot, AnalyzeProps props) {
        this.guard = guard;
        this.normalizer = normalizer;
        this.masker = masker;
        this.extractor = extractor;
        this.engine = engine;
        this.classifier = classifier;
        this.analogies = analogies;
        this.rules = rules.ruleSet();
        this.i18n = i18n;
        this.snapshot = snapshot;
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

        List<SignalHit> hits = engine.evaluate(text);
        Band band = BandCalculator.band(hits, ctx.unreadable());
        ContentClass contentClass = ctx.unreadable() ? ContentClass.UNKNOWN : classifier.classify(text, hits);
        String analogyKey = ctx.unreadable() ? null : analogies.select(text, hits).orElse(null);
        AnalyzeResponse response = respond(ctx, hits, band, contentClass, analogyKey, Engine.TEMPLATE);

        LOG.info(LogEvent.ANALYZED, tag(LogKey.SOURCE, ctx.source()), tag(LogKey.BAND, band),
                tag(LogKey.ENGINE, response.engine()), num(LogKey.COUNT, response.signals().size()),
                duration(LogKey.DURATION_MS, Duration.ofNanos(System.nanoTime() - started)));
        return response;
    }

    private AnalyzeResponse respond(AnalysisContext ctx, List<SignalHit> hits, Band band, ContentClass contentClass,
                                    String analogyKey, Engine engine) {
        List<AnalyzeResponse.Signal> signals = new ArrayList<>();
        List<AnalyzeResponse.Unverified> unverified = new ArrayList<>();
        List<AnalyzeResponse.Reassuring> reassuring = new ArrayList<>();
        for (SignalHit hit : hits) {
            SignalRule rule = rules.require(hit.id());
            if (hit.severity().countsTowardBand()) {
                signals.add(new AnalyzeResponse.Signal(hit.id(), hit.severity(), hit.evidence(), reason(ctx, rule)));
            } else if (hit.severity() == Severity.UNVERIFIED) {
                unverified.add(new AnalyzeResponse.Unverified(hit.id(), hit.item(), rule.action(), hit.snapshot()));
            } else {
                reassuring.add(new AnalyzeResponse.Reassuring(hit.id(), hit.evidence(), reason(ctx, rule)));
            }
        }
        return new AnalyzeResponse(ctx.lang(), ctx.entities(), signals, unverified, reassuring, band, contentClass,
                new AnalyzeResponse.Counts(signals.size(), unverified.size(), reassuring.size()), List.of(),
                analogyKey, AnalyzeResponse.FOOTER_KEY, engine);
    }

    private String reason(AnalysisContext ctx, SignalRule rule) {
        String date = snapshot.date().map(Object::toString).orElse("");
        return i18n.text(ctx.lang(), rule.reasonKey(), Map.of("date", date));
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
