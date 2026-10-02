package in.ruko.rules;

import static org.assertj.core.api.Assertions.assertThat;

import in.ruko.pipeline.AnalysisContext;
import in.ruko.pipeline.AnalysisService;
import in.ruko.support.Pipeline;
import in.ruko.support.Pipeline.Fixture;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Every hit on every fixture carries evidence quoted from the request: each part between placeholders is a
 * verbatim substring of the input, and the whole quote is a substring of the input with personal data masked.
 */
class EverySignalHasSpanTest {

    private final AnalysisService service = Pipeline.service();
    private final SignalEngine engine = new SignalEngine(Pipeline.RULES, Pipeline.shippedSnapshot());

    @Test
    void everyHitQuotesTheInput() {
        int checked = 0;
        for (Fixture fixture : Pipeline.fixtures()) {
            AnalysisContext ctx = service.prepare(fixture.request());
            RuleText text = RuleText.of(ctx);
            String input = fixture.text();
            String maskedInput = text.quote(0, text.length());
            List<SignalHit> hits = engine.evaluate(text);
            for (SignalHit hit : hits) {
                String where = fixture.id() + " " + hit.id();
                assertThat(hit.evidence()).as(where).isNotBlank();
                assertThat(maskedInput).as(where).contains(hit.evidence());
                for (String part : hit.evidence().split("\\[(?:PHONE|ACCT|AADHAAR|PAN|OTP)]")) {
                    assertThat(input).as(where).contains(part);
                }
                if (hit.item() != null) {
                    assertThat(input.toUpperCase(Locale.ROOT)).as(where).contains(hit.item().toUpperCase(Locale.ROOT));
                }
                checked++;
            }
        }
        assertThat(checked).isGreaterThanOrEqualTo(70);
    }

    @Test
    void maskedPersonalDataNeverAppearsInEvidence() {
        for (Fixture fixture : Pipeline.fixtures()) {
            RuleText text = RuleText.of(service.prepare(fixture.request()));
            for (SignalHit hit : engine.evaluate(text)) {
                assertThat(hit.evidence()).as(fixture.id())
                        .doesNotContain("12345678901", "2345 6789 0123", "482913", "ABCPE1234F", "98765 43210");
            }
        }
    }
}
