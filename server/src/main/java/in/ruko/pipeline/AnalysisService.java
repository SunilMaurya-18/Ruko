package in.ruko.pipeline;

import static in.ruko.infra.SafeLog.duration;
import static in.ruko.infra.SafeLog.num;
import static in.ruko.infra.SafeLog.tag;

import in.ruko.api.dto.AnalyzeRequest;
import in.ruko.api.dto.AnalyzeResponse;
import in.ruko.extract.Entities;
import in.ruko.extract.EntityExtractor;
import in.ruko.infra.LogEvent;
import in.ruko.infra.LogKey;
import in.ruko.infra.SafeLog;
import in.ruko.infra.config.AnalyzeProps;
import in.ruko.rules.Band;
import in.ruko.rules.ContentClass;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {

    private static final SafeLog LOG = SafeLog.of(AnalysisService.class);
    private static final double MIN_LETTER_RATIO = 0.5;

    private final InputGuard guard;
    private final TextNormalizer normalizer;
    private final PiiMasker masker;
    private final EntityExtractor extractor;
    private final AnalyzeProps props;

    public AnalysisService(InputGuard guard, TextNormalizer normalizer, PiiMasker masker, EntityExtractor extractor,
                           AnalyzeProps props) {
        this.guard = guard;
        this.normalizer = normalizer;
        this.masker = masker;
        this.extractor = extractor;
        this.props = props;
    }

    public AnalysisContext prepare(AnalyzeRequest request) {
        guard.check(request.text());
        MappedText normalized = normalizer.normalize(request.text());
        MappedText masked = masker.mask(normalized);
        Entities entities = extractor.extract(masked.value());
        boolean unreadable = unreadable(masked.value(), request.ocrConfidence());
        return new AnalysisContext(normalized, masked, entities, request.lang(), request.source(), unreadable);
    }

    public AnalyzeResponse analyze(AnalyzeRequest request) {
        long started = System.nanoTime();
        AnalysisContext ctx = prepare(request);

        Band band = ctx.unreadable() ? Band.NOT_ENOUGH_TO_JUDGE : Band.FEW_FLAGS_STILL_VERIFY;
        Engine engine = Engine.TEMPLATE;
        AnalyzeResponse response = new AnalyzeResponse(ctx.lang(), ctx.entities(), List.of(), List.of(), List.of(),
                band, ContentClass.UNKNOWN, new AnalyzeResponse.Counts(0, 0, 0), List.of(), null,
                AnalyzeResponse.FOOTER_KEY, engine);

        LOG.info(LogEvent.ANALYZED, tag(LogKey.SOURCE, ctx.source()), tag(LogKey.BAND, band),
                tag(LogKey.ENGINE, engine), num(LogKey.COUNT, response.signals().size()),
                duration(LogKey.DURATION_MS, Duration.ofNanos(System.nanoTime() - started)));
        return response;
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
