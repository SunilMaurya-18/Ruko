package in.ruko.rules;

import static org.assertj.core.api.Assertions.assertThat;

import in.ruko.api.dto.AnalyzeRequest;
import in.ruko.pipeline.AnalysisContext;
import in.ruko.pipeline.AnalysisService;
import in.ruko.pipeline.Lang;
import in.ruko.pipeline.Source;
import in.ruko.support.Pipeline;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContentClassifierTest {

    private final AnalysisService service = Pipeline.service();
    private final ContentClassifier classifier = new ContentClassifier(Pipeline.RULES.ruleSet());

    private AnalysisContext ctx(String text) {
        return service.prepare(new AnalyzeRequest(text, Lang.EN, Source.PASTE, null));
    }

    private ContentClass classify(String text, String... ids) {
        List<SignalHit> hits = List.of(ids).stream().map(id -> Pipeline.RULES.ruleSet().require(id))
                .map(rule -> new SignalHit(rule.id(), rule.severity(), "evidence", null, null)).toList();
        return classifier.classify(RuleText.of(ctx(text)), hits);
    }

    @Test
    void anyCriticalOrStrongSignalIsPromotionEvenWithAnExplanation() {
        assertThat(classify("What is a stock? Learn it here.", "C1", "R2")).isEqualTo(ContentClass.PROMOTION);
        assertThat(classify("What is a stock? Learn it here.", "S6")).isEqualTo(ContentClass.PROMOTION);
    }

    @Test
    void explanationWithAPaymentAskIsMixed() {
        assertThat(classify("Learn candlestick charts in our weekend workshop. Fee Rs 2,499 per seat."))
                .isEqualTo(ContentClass.MIXED);
    }

    @Test
    void paymentAskWithoutExplanationIsPromotion() {
        assertThat(classify("Our advisory fees are Rs 1,000 per month, payable in advance."))
                .isEqualTo(ContentClass.PROMOTION);
        assertThat(classify("Pay fees only to our UPI handle advisor.ia@validicici.", "R1"))
                .isEqualTo(ContentClass.PROMOTION);
    }

    @Test
    void reassuranceR2AloneIsEducation() {
        assertThat(classify("What is an index fund? It follows a market index.", "R2"))
                .isEqualTo(ContentClass.EDUCATION);
        assertThat(classify("What is an index fund? It follows a market index.", "R2", "M13"))
                .isEqualTo(ContentClass.EDUCATION);
    }

    @Test
    void moderateOnlyOrNothingIsUnknown() {
        assertThat(classify("Markets may rise next week. Limited seats in our free webinar.", "M11"))
                .isEqualTo(ContentClass.UNKNOWN);
        assertThat(classify("Mere broker ne bola hai ki ye share accha hai.")).isEqualTo(ContentClass.UNKNOWN);
    }

    @Test
    void unreadableTextIsUnknown() {
        AnalysisContext unreadable = service.prepare(
                new AnalyzeRequest("Fee Rs 4,999 workshop learn", Lang.EN, Source.OCR, 0.2));
        assertThat(classifier.classify(unreadable, List.of())).isEqualTo(ContentClass.UNKNOWN);
    }
}
