package in.ruko.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import in.ruko.api.dto.AnalyzeRequest;
import in.ruko.api.dto.AnalyzeResponse;
import in.ruko.pipeline.Lang;
import in.ruko.pipeline.Source;
import in.ruko.rules.Band;
import in.ruko.rules.ContentClass;
import in.ruko.rules.Severity;
import in.ruko.snapshot.SebiSnapshotIndex;
import in.ruko.support.Pipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Exit gate for Phase 3: zero violations on every template output (each fixture in both languages, with the
 * shipped empty snapshot and a dated one), and every hand-written bad output caught by the rule it targets.
 */
class LinterTest {

    private static final GuardrailLinter LINTER = Pipeline.LINTER;
    private static final AtomicInteger OUTPUTS = new AtomicInteger();

    private static final String SCAM_EN = "Guaranteed 5% daily returns on your investment! Join our VIP group for just "
            + "Rs 4999. Pay to vipprofits@okaxis and send screenshot. Only 10 seats left today.";

    static Stream<Arguments> templateOutputs() {
        List<Arguments> cases = new ArrayList<>();
        for (Pipeline.Fixture fixture : Pipeline.fixtures()) {
            for (Lang lang : Lang.values()) {
                cases.add(Arguments.of(fixture.id() + "/" + lang.wire() + "/shipped", fixture, lang, Pipeline.shippedSnapshot()));
                cases.add(Arguments.of(fixture.id() + "/" + lang.wire() + "/dated", fixture, lang, Pipeline.datedSnapshot()));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("templateOutputs")
    void everyTemplateOutputIsClean(String name, Pipeline.Fixture fixture, Lang lang, SebiSnapshotIndex snapshot) {
        Pipeline.Drafted drafted = Pipeline.drafted(
                new AnalyzeRequest(fixture.text(), lang, fixture.source(), fixture.ocrConfidence()), snapshot);

        LintResult result = LINTER.lint(drafted.response(), drafted.context());

        OUTPUTS.incrementAndGet();
        assertThat(result.violations()).as(name).isEmpty();
        assertThat(drafted.response().cards()).extracting(AnalyzeResponse.Card::signalId).doesNotContainNull();
    }

    @Test
    void genericFallbackIsClean() {
        for (Lang lang : Lang.values()) {
            Pipeline.Drafted drafted = scam(lang);
            AnalyzeResponse generic = FallbackTemplates.generic(lang, drafted.response().entities(),
                    drafted.response().band(), drafted.response().contentClass(), drafted.response().counts());
            OUTPUTS.incrementAndGet();
            assertThat(LINTER.lint(generic, drafted.context()).violations()).isEmpty();
        }
    }

    static Stream<Arguments> badCards() {
        return Stream.of(
                // L1 recommendation language
                bad(LintCode.L1, Lang.EN, "Buy this plan before the seats run out."),
                bad(LintCode.L1, Lang.EN, "You should sell your other shares first."),
                bad(LintCode.L1, Lang.EN, "Hold your units for one more month."),
                bad(LintCode.L1, Lang.EN, "Accumulate slowly and book profit later."),
                bad(LintCode.L1, Lang.EN, "We recommend joining only after checking."),
                bad(LintCode.L1, Lang.EN, "Invest now while the offer lasts."),
                bad(LintCode.L1, Lang.EN, "Strong buy signal in this group."),
                bad(LintCode.L1, Lang.EN, "Abhi kharido, kal mauka nahi milega."),
                bad(LintCode.L1, Lang.EN, "Hold karo bhai, sab theek hoga."),
                bad(LintCode.L1, Lang.EN, "Paisa lagao aur aaram karo."),
                bad(LintCode.L1, Lang.HI, "यह योजना अभी खरीदो, फिर मौका नहीं मिलेगा।"),
                bad(LintCode.L1, Lang.HI, "बाकी शेयर बेचो और यहाँ पैसा लगाओ।"),
                bad(LintCode.L1, Lang.HI, "होल्ड करो, घबराने की बात नहीं।"),
                bad(LintCode.L1, Lang.HI, "आज ही निवेश करें और देखें।"),
                // L2 tickers
                bad(LintCode.L2, Lang.EN, "This message is about RELIANCE and nothing else."),
                bad(LintCode.L2, Lang.EN, "It mentions TCS in the first line."),
                bad(LintCode.L2, Lang.EN, "Reliance is named in the message."),
                bad(LintCode.L2, Lang.EN, "The tip names NSE: ABCX for tomorrow."),
                bad(LintCode.L2, Lang.EN, "It quotes BSE:500325 as the code."),
                bad(LintCode.L2, Lang.EN, "Shares of HDFCBANK are mentioned here."),
                bad(LintCode.L2, Lang.EN, "Sunpharma appears in this message."),
                bad(LintCode.L2, Lang.EN, "The tip is about M&M only."),
                bad(LintCode.L2, Lang.HI, "इस मैसेज में RELIANCE का नाम है।"),
                bad(LintCode.L2, Lang.HI, "इसमें INFY का ज़िक्र है।"),
                // L3 verdicts
                bad(LintCode.L3, Lang.EN, "This message looks safe to me."),
                bad(LintCode.L3, Lang.EN, "This is clearly a scam."),
                bad(LintCode.L3, Lang.EN, "The sender is a fraud."),
                bad(LintCode.L3, Lang.EN, "The adviser is genuine and verified."),
                bad(LintCode.L3, Lang.EN, "A legit offer from a trusted group."),
                bad(LintCode.L3, Lang.EN, "Ye bilkul farzi group hai."),
                bad(LintCode.L3, Lang.HI, "यह मैसेज सुरक्षित है।"),
                bad(LintCode.L3, Lang.HI, "भेजने वाला धोखेबाज़ है।"),
                bad(LintCode.L3, Lang.HI, "यह पूरा फ्रॉड है।"),
                bad(LintCode.L3, Lang.HI, "यह स्कैम है, पैसा मत भेजो।"),
                bad(LintCode.L3, Lang.HI, "यह ग्रुप फ़र्ज़ी है।"),
                bad(LintCode.L3, Lang.HI, "यह सलाहकार भरोसेमंद है।"),
                // L4 card length and blank cards
                bad(LintCode.L4, Lang.EN, "This card goes on and on with far too many words for anyone to hear in one go, "
                        + "and it keeps adding more and more words until it is well past the limit."),
                bad(LintCode.L4, Lang.EN, "   "),
                bad(LintCode.L4, Lang.HI, "यह कार्ड बहुत लंबा है और इसमें इतने ज़्यादा शब्द हैं कि कोई भी इसे एक बार में सुन नहीं पाएगा "
                        + "और यह लगातार बढ़ता ही जाता है जब तक सीमा पार न हो जाए।"),
                // L5 links and markup
                bad(LintCode.L5, Lang.EN, "Read more at https://evil.example/offer now."),
                bad(LintCode.L5, Lang.EN, "See www.fake-sebi-check.com for details."),
                bad(LintCode.L5, Lang.EN, "Go to sebi-check-help.in to learn more."),
                bad(LintCode.L5, Lang.EN, "Call tel:9999999999 for help."),
                bad(LintCode.L5, Lang.EN, "<b>Stop</b> before you pay."),
                bad(LintCode.L5, Lang.EN, "**Stop** before you pay."),
                bad(LintCode.L5, Lang.EN, "Check [SEBI](https://siportal.sebi.gov.in) first."),
                bad(LintCode.L5, Lang.EN, "Open javascript:void(0) to continue."),
                bad(LintCode.L5, Lang.EN, "Use `SEBI Check` before you pay."),
                bad(LintCode.L5, Lang.EN, "Visit http://siportal.sebi.gov.in before you pay."),
                // L6 language
                bad(LintCode.L6, Lang.EN, "पैसा भेजने से पहले रुकें।"),
                bad(LintCode.L6, Lang.HI, "Stop before you send any money."),
                bad(LintCode.L6, Lang.HI, "Paisa bhejne se pehle ruko aur dekho."),
                // L7 predictions
                bad(LintCode.L7, Lang.EN, "It could give 30% a month."),
                bad(LintCode.L7, Lang.EN, "Target is near for this one."),
                bad(LintCode.L7, Lang.EN, "The price will rise after Friday."),
                bad(LintCode.L7, Lang.EN, "You get Rs 500 every day."),
                bad(LintCode.L7, Lang.EN, "Only ₹999 to join."),
                bad(LintCode.L7, Lang.EN, "Your money will double soon."),
                bad(LintCode.L7, Lang.EN, "Expect 10x in a year."),
                bad(LintCode.L7, Lang.EN, "Ye share upar jayega."),
                bad(LintCode.L7, Lang.HI, "इसका दाम बढ़ेगा।"),
                bad(LintCode.L7, Lang.HI, "पैसा दोगुना हो जाएगा।"),
                bad(LintCode.L7, Lang.HI, "टारगेट पास है।"),
                bad(LintCode.L7, Lang.HI, "हर दिन 5000 रुपये मिलेंगे।"),
                // A quote is only exempt when it is verbatim from the input.
                bad(LintCode.L7, Lang.EN, "It says “Guaranteed 50% daily returns” to pull you in."));
    }

    private static Arguments bad(LintCode code, Lang lang, String card) {
        return Arguments.of(code, lang, card);
    }

    @ParameterizedTest(name = "{0} {1}: {2}")
    @MethodSource("badCards")
    void badCardIsCaught(LintCode code, Lang lang, String card) {
        Pipeline.Drafted drafted = scam(lang);
        AnalyzeResponse draft = withCard(drafted.response(), card);

        OUTPUTS.incrementAndGet();
        assertThat(LINTER.lint(draft, drafted.context()).codes()).contains(code);
    }

    @Test
    void verbatimQuoteFromTheInputIsAllowed() {
        Pipeline.Drafted drafted = scam(Lang.EN);
        AnalyzeResponse draft = withCard(drafted.response(), "It promises “Guaranteed 5% daily returns” to pull you in.");

        OUTPUTS.incrementAndGet();
        assertThat(LINTER.lint(draft, drafted.context()).violations()).isEmpty();
    }

    @Test
    void allowlistedLinkIsAllowed() {
        Pipeline.Drafted drafted = scam(Lang.EN);
        AnalyzeResponse draft = withCard(drafted.response(),
                "Look it up on https://siportal.sebi.gov.in/intermediary/sebi-check or call tel:1930.");

        OUTPUTS.incrementAndGet();
        assertThat(LINTER.lint(draft, drafted.context()).violations()).isEmpty();
    }

    @Test
    void evidenceAndItemsMustComeFromTheInput() {
        Pipeline.Drafted drafted = Pipeline.drafted(new AnalyzeRequest(
                "Guaranteed returns! Our research analyst INH000012345 says pay 5000 now to fast@okaxis.",
                Lang.EN, Source.SHARE, null), Pipeline.shippedSnapshot());
        AnalyzeResponse r = drafted.response();

        List<AnalyzeResponse.Signal> signals = new ArrayList<>(r.signals());
        AnalyzeResponse.Signal first = signals.getFirst();
        signals.set(0, new AnalyzeResponse.Signal(first.id(), first.severity(), "Guaranteed 90% returns", first.reason()));
        AnalyzeResponse badEvidence = copy(r, signals, r.unverified(), r.reassuring(), r.cards(), r.band(),
                r.contentClass(), r.analogyKey(), r.footerKey());

        AnalyzeResponse.Unverified u14 = r.unverified().getFirst();
        AnalyzeResponse badItem = copy(r, r.signals(),
                List.of(new AnalyzeResponse.Unverified(u14.id(), "INH999999999", u14.action(), null)),
                r.reassuring(), r.cards(), r.band(), r.contentClass(), r.analogyKey(), r.footerKey());

        OUTPUTS.addAndGet(2);
        assertThat(LINTER.lint(badEvidence, drafted.context()).violations())
                .containsExactly(new Violation(LintCode.L4, "signals[0].evidence"));
        assertThat(LINTER.lint(badItem, drafted.context()).violations())
                .containsExactly(new Violation(LintCode.L4, "unverified[0].item"));
    }

    @Test
    void draftMustSayWhatTheRulesSaid() {
        Pipeline.Drafted drafted = scam(Lang.EN);
        AnalyzeResponse r = drafted.response();
        LintContext ctx = drafted.context();

        AnalyzeResponse.Signal first = r.signals().getFirst();
        List<AnalyzeResponse.Signal> softened = new ArrayList<>(r.signals());
        softened.set(0, new AnalyzeResponse.Signal(first.id(), Severity.MODERATE, first.evidence(), first.reason()));

        Map<String, AnalyzeResponse> drafts = Map.of(
                "band", copy(r, r.signals(), r.unverified(), r.reassuring(), r.cards(), Band.FEW_FLAGS_STILL_VERIFY,
                        r.contentClass(), r.analogyKey(), r.footerKey()),
                "content_class", copy(r, r.signals(), r.unverified(), r.reassuring(), r.cards(), r.band(),
                        ContentClass.EDUCATION, r.analogyKey(), r.footerKey()),
                "analogy_key", copy(r, r.signals(), r.unverified(), r.reassuring(), r.cards(), r.band(),
                        r.contentClass(), "analogy.remote_access", r.footerKey()),
                "signals[0]", copy(r, softened, r.unverified(), r.reassuring(), r.cards(), r.band(),
                        r.contentClass(), r.analogyKey(), r.footerKey()),
                "cards[0].signal_id", copy(r, r.signals(), r.unverified(), r.reassuring(),
                        List.of(new AnalyzeResponse.Card("C3", "Asks you to install a screen-sharing app.")), r.band(),
                        r.contentClass(), r.analogyKey(), r.footerKey()),
                "reassuring[0]", copy(r, r.signals(), r.unverified(),
                        List.of(new AnalyzeResponse.Reassuring("R2", "Join our VIP group", "Reads like general investor education.")),
                        r.cards(), r.band(), r.contentClass(), r.analogyKey(), r.footerKey()));

        drafts.forEach((field, draft) -> {
            OUTPUTS.incrementAndGet();
            assertThat(LINTER.lint(draft, ctx).violations()).as(field).contains(new Violation(LintCode.L8, field));
        });
    }

    @Test
    void onlyTheFixedFooterIsAllowed() {
        Pipeline.Drafted drafted = scam(Lang.EN);
        AnalyzeResponse r = drafted.response();
        AnalyzeResponse draft = copy(r, r.signals(), r.unverified(), r.reassuring(), r.cards(), r.band(),
                r.contentClass(), r.analogyKey(), "looks_safe");

        OUTPUTS.incrementAndGet();
        assertThat(LINTER.lint(draft, drafted.context()).violations())
                .containsExactly(new Violation(LintCode.L3, "footer_key"));
    }

    @Test
    void droppingTheAnalogyIsAllowed() {
        Pipeline.Drafted drafted = scam(Lang.HI);
        AnalyzeResponse r = drafted.response();

        OUTPUTS.incrementAndGet();
        assertThat(LINTER.lint(copy(r, r.signals(), r.unverified(), r.reassuring(), r.cards(), r.band(),
                r.contentClass(), null, r.footerKey()), drafted.context()).violations()).isEmpty();
    }

    @AfterAll
    static void lintedAtLeastTwoHundredOutputs() {
        assertThat(OUTPUTS.get()).isGreaterThanOrEqualTo(200);
    }

    static Pipeline.Drafted scam(Lang lang) {
        return Pipeline.drafted(new AnalyzeRequest(SCAM_EN, lang, Source.SHARE, null), Pipeline.shippedSnapshot());
    }

    static AnalyzeResponse withCard(AnalyzeResponse r, String text) {
        List<AnalyzeResponse.Card> cards = new ArrayList<>(r.cards());
        cards.set(0, new AnalyzeResponse.Card(cards.getFirst().signalId(), text));
        return copy(r, r.signals(), r.unverified(), r.reassuring(), cards, r.band(), r.contentClass(), r.analogyKey(),
                r.footerKey());
    }

    private static AnalyzeResponse copy(AnalyzeResponse r, List<AnalyzeResponse.Signal> signals,
                                        List<AnalyzeResponse.Unverified> unverified,
                                        List<AnalyzeResponse.Reassuring> reassuring, List<AnalyzeResponse.Card> cards,
                                        Band band, ContentClass contentClass, String analogyKey, String footerKey) {
        return new AnalyzeResponse(r.language(), r.entities(), signals, unverified, reassuring, band, contentClass,
                r.counts(), cards, analogyKey, footerKey, r.engine());
    }
}
