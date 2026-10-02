package in.ruko.rules;

import static org.assertj.core.api.Assertions.assertThat;

import in.ruko.api.dto.AnalyzeRequest;
import in.ruko.pipeline.AnalysisService;
import in.ruko.pipeline.Lang;
import in.ruko.pipeline.Source;
import in.ruko.support.Pipeline;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Per-rule cases written apart from the fixtures: each signal fires on a new positive and stays off a near miss. */
class SignalRulesTest {

    private final SignalEngine engine = new SignalEngine(Pipeline.RULES, Pipeline.shippedSnapshot());
    private final AnalysisService service = Pipeline.service();

    private List<SignalHit> hits(String text, Source source) {
        return engine.evaluate(RuleText.of(service.prepare(new AnalyzeRequest(text, Lang.EN, source, null))));
    }

    private List<String> ids(String text, Source source) {
        return hits(text, source).stream().map(SignalHit::id).toList();
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                fires("C1", "Invest 10000 and get assured returns every month."),
                fires("C1", "Paisa double hoga 10 din me pakka, bhai."),
                quiet("C1", "No adviser can give guaranteed returns, so beware of such claims."),
                quiet("C1", "कोई भी गारंटी वाला मुनाफा नहीं होता।"),
                quiet("C1", "Remember, no registered adviser can promise guaranteed returns."),
                quiet("C1", "Nobody can guarantee returns in the stock market."),
                quiet("C1", "A real adviser cannot promise assured returns."),
                fires("C1", "No other platform can guarantee you assured returns like us."),
                fires("C1", "Guaranteed returns every month, no risk at all."),
                fires("C1", "Listing pe paisa 3 guna hoga, bas abhi lagao."),
                quiet("C1", "Lambe samay me SIP ka paisa 3 guna ho sakta hai."),
                fires("C2", "Send 5000 to rahul.trades@okicici for the gold plan."),
                fires("C2", "Pay the joining amount to account 123456789012345 today."),
                quiet("C2", "Pay only to the broker handle abc.brk@validhdfc for your trades."),
                quiet("C2", "My UPI is rahul@okicici. Message me about the meetup."),
                fires("C2", "Joining fee 2000 GPay karo 9876543210 pe."),
                quiet("C2", "Pay the fee in the app. For help call 9876543210."),
                fires("C3", "Download TeamViewer so that I can help you place trades."),
                fires("C3", "एनीडेस्क डाउनलोड करें और कोड बताएं।"),
                quiet("C3", "Never install TeamViewer because a caller asked you to."),
                fires("C4", "Get our trading app here: https://trade-fast.example/app/TradeFast.apk"),
                quiet("C4", "Download trading apps only from the Play Store or the App Store."),
                fires("C4", "Install Verify.apk today to avoid suspension of your account."),
                quiet("C4", "Avoid installing any .apk file a stranger sends you."),
                fires("C15", "This is a call from the SEBI office about your pending KYC."),
                fires("C15", "मैं सेबी से बोल रहा हूँ, आपका खाता बंद होगा।"),
                quiet("C15", "SEBI publishes investor education material on its website."),
                fires("C15", "Message from the NSE compliance team about your account."),
                fires("C16", "Your dividend is ready. Pay Rs 999 processing fee to release it."),
                quiet("C16", "Short-term capital gains tax applies to profit from shares sold within a year."),
                fires("C17", "Scan the QR code and pay 499 to join our tips group."),
                quiet("C17", "Scan the QR code on the bank leaflet to read the product details."),
                fires("S5", "SEBI registered research analyst, registration INZ000123456."),
                fires("S5", "We are an investment adviser, Reg No INA1234567."),
                quiet("S5", "SEBI registered research analyst, registration INH000012345."),
                fires("S6", "Our fund is SEBI approved and fully transparent."),
                quiet("S6", "SEBI never approves tips, so avoid anyone who says SEBI approved."),
                fires("S7", "Join our premium channel, only Rs 1499 per month."),
                quiet("S7", "Premium quality mangoes from Ratnagiri are in season."),
                quiet("S7", "Send 500 to vip.club@ybl for the book you ordered."),
                fires("S8", "Send money to our account and we will credit shares in your demat."),
                quiet("S8", "We never ask you to send money to us."),
                fires("S9", "Tomorrow XYZLTD will hit 75 before noon."),
                quiet("S9", "Nifty closed near 24000 today after a flat session."),
                fires("M11", "Hurry, only 5 seats left in the batch!"),
                quiet("M11", "Report online fraud immediately by calling 1930."),
                fires("M11", "Update your KYC now to avoid account suspension."),
                quiet("M11", "Units are credited to your demat account within 24 hours."),
                fires("M13", "Mukesh Ambani is backing this new trading platform."),
                quiet("M13", "A new trading platform has launched in our city."),
                fires("U14", "Registered research analyst INH000012345 shares weekly notes."),
                fires("R1", "Pay the advisory fee to advisor@validhdfc only."),
                quiet("R1", "Pay the advisory fee to advisor@okhdfcbank only."),
                fires("R2", "What is a mutual fund? It pools money from many investors."),
                quiet("R2", "Learn trading basics in our class. Fee Rs 999, pay to coach@okaxis."));
    }

    private static Arguments fires(String id, String text) {
        return Arguments.of(id, true, text);
    }

    private static Arguments quiet(String id, String text) {
        return Arguments.of(id, false, text);
    }

    @ParameterizedTest(name = "{0} fires={1}: {2}")
    @MethodSource("cases")
    void ruleFiresOnlyWhenItShould(String id, boolean fires, String text) {
        List<String> ids = ids(text, Source.PASTE);
        if (fires) {
            assertThat(ids).contains(id);
        } else {
            assertThat(ids).doesNotContain(id);
        }
    }

    @Test
    void forwardedVideoOrAudioAlwaysRaisesM13() {
        assertThat(ids("Watch this clip about a new trading app.", Source.VIDEO)).contains("M13");
        assertThat(ids("Listen to this message about a new trading app.", Source.AUDIO)).contains("M13");
        assertThat(ids("Watch this clip about a new trading app.", Source.SHARE)).doesNotContain("M13");
    }

    @Test
    void personalPayeeEvidenceQuotesThePayVerbAndTheHandle() {
        SignalHit c2 = hits("Hello sir. Send 5000 to rahul.trades@okicici for the gold plan.", Source.PASTE).stream()
                .filter(hit -> hit.id().equals("C2")).findFirst().orElseThrow();
        assertThat(c2.evidence()).isEqualTo("Send 5000 to rahul.trades@okicici");
    }

    @Test
    void evidenceKeepsPersonalDataMasked() {
        SignalHit c2 = hits("Pay the joining amount to account 123456789012345 today.", Source.PASTE).stream()
                .filter(hit -> hit.id().equals("C2")).findFirst().orElseThrow();
        assertThat(c2.evidence()).contains("[ACCT]").doesNotContain("123456789012345");
    }

    @Test
    void obfuscatedTermsAreQuotedAsWritten() {
        SignalHit c3 = hits("Bhai a.n.y.d.e.s.k install karo abhi.", Source.PASTE).stream()
                .filter(hit -> hit.id().equals("C3")).findFirst().orElseThrow();
        assertThat(c3.evidence()).isEqualTo("a.n.y.d.e.s.k");
    }

    @Test
    void oneU14PerDistinctRegistrationNumber() {
        List<SignalHit> u14 = hits("Adviser INA000098765 and analyst INH000067890. Again INA000098765.", Source.PASTE)
                .stream().filter(hit -> hit.id().equals("U14")).toList();
        assertThat(u14).extracting(SignalHit::item).containsExactly("INA000098765", "INH000067890");
    }

    @Test
    void hitsAreOrderedBySeverityThenCatalogue() {
        List<SignalHit> hits = hits("Hurry! Guaranteed 10% monthly. SEBI approved. Pay to vip.club@ybl now. "
                + "Adviser INA000098765.", Source.PASTE);
        assertThat(hits).extracting(SignalHit::severity).isSortedAccordingTo(Enum::compareTo);
        assertThat(hits).extracting(SignalHit::id).startsWith("C1", "C2");
    }
}
