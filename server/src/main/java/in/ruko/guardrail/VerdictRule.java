package in.ruko.guardrail;

import in.ruko.api.dto.AnalyzeResponse;
import java.util.List;
import java.util.regex.Pattern;

/**
 * L3: no verdicts. Ruko reports signs; it never calls a message safe, genuine, a scam, or a fraud. The fixed footer
 * key is the only exception, and the draft may carry no other footer.
 */
final class VerdictRule implements LintRule {

    private static final Pattern VERDICT = LintText.words(List.of(
            "safe", "safer", "unsafe", "scam", "scams", "scammer", "scammers", "scammy", "fraud", "frauds",
            "fraudulent", "fraudster", "fraudsters", "genuine", "legit", "legitimate", "verified", "trustworthy",
            "trusted", "सुरक्षित", "असुरक्षित", "धोखेबाज़", "धोखेबाज", "धोखाधड़ी", "धोखा", "फ्रॉड", "फ़्रॉड", "स्कैम",
            "ठग", "ठगी", "फर्जी", "फ़र्ज़ी", "फर्ज़ी", "भरोसेमंद",
            "dhokha", "dhokebaaz", "dhokhebaaz", "thag", "thagi", "farzi", "bharosemand", "surakshit"));

    @Override
    public LintCode code() {
        return LintCode.L3;
    }

    @Override
    public void check(Draft draft, LintContext ctx, List<Violation> out) {
        if (draft.response() != null && !AnalyzeResponse.FOOTER_KEY.equals(draft.response().footerKey())) {
            out.add(new Violation(code(), "footer_key"));
        }
        for (Text text : draft.texts()) {
            if (VERDICT.matcher(LintText.unquoted(text.value(), ctx.input())).find()) {
                out.add(new Violation(code(), text.field()));
            }
        }
    }
}
