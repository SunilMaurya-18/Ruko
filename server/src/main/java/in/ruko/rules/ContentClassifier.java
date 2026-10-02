package in.ruko.rules;

import in.ruko.pipeline.AnalysisContext;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Track E class from TRD §2, derived after signals and never by a model alone:
 * <ul>
 *   <li>{@code promotion}: any critical or strong signal (these include return promises and VIP fees), or a
 *       payment ask with no explanation;</li>
 *   <li>{@code mixed}: an explanation together with a payment ask;</li>
 *   <li>{@code education}: R2, with no critical or strong signal;</li>
 *   <li>{@code unknown}: anything else, including unreadable text.</li>
 * </ul>
 */
@Component
public class ContentClassifier {

    public static final String EDUCATION_SIGNAL = "R2";

    private final RuleSet rules;

    @Autowired
    public ContentClassifier(RuleLoader loader) {
        this(loader.ruleSet());
    }

    ContentClassifier(RuleSet rules) {
        this.rules = rules;
    }

    public ContentClass classify(AnalysisContext ctx, List<SignalHit> hits) {
        return ctx.unreadable() ? ContentClass.UNKNOWN : classify(RuleText.of(ctx), hits);
    }

    public ContentClass classify(RuleText text, List<SignalHit> hits) {
        if (hits.stream().anyMatch(hit -> hit.severity() == Severity.CRITICAL || hit.severity() == Severity.STRONG)) {
            return ContentClass.PROMOTION;
        }
        if (rules.marker(RuleSet.PAYMENT_ASK, text)) {
            return rules.marker(RuleSet.EXPLANATION, text) ? ContentClass.MIXED : ContentClass.PROMOTION;
        }
        if (hits.stream().anyMatch(hit -> hit.id().equals(EDUCATION_SIGNAL))) {
            return ContentClass.EDUCATION;
        }
        return ContentClass.UNKNOWN;
    }
}
