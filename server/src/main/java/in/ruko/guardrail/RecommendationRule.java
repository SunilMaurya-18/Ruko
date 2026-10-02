package in.ruko.guardrail;

import java.util.List;
import java.util.regex.Pattern;

/** L1: Ruko never tells anyone to buy, sell, hold, or invest. */
final class RecommendationRule implements LintRule {

    private static final Pattern ADVICE = LintText.words(List.of(
            "buy", "sell", "hold", "buy now", "sell now", "accumulate", "book profit", "book profits", "add more",
            "go long", "go short", "exit now", "invest now", "must invest", "should invest", "should buy",
            "strong buy", "recommend", "recommended", "recommendation", "we suggest", "best stock", "best share",
            "खरीदो", "खरीदें", "खरीदिए", "खरीद लो", "खरीद लें", "ख़रीदो", "ख़रीदें", "ख़रीदिए", "ख़रीद लो", "ख़रीद लें",
            "बेचो", "बेचें", "बेचिए", "बेच दो", "बेच दें", "होल्ड करो", "होल्ड करें", "पकड़े रहो", "निवेश करो",
            "निवेश करें", "पैसा लगाओ", "पैसे लगाओ", "लगा दो", "ले लो",
            "kharido", "kharid lo", "kharid lein", "kharidein", "becho", "bech do", "bechein", "hold karo",
            "hold karein", "buy karo", "sell karo", "le lo", "invest karo", "paisa lagao", "abhi lo"));

    @Override
    public LintCode code() {
        return LintCode.L1;
    }

    @Override
    public void check(Draft draft, LintContext ctx, List<Violation> out) {
        for (Text text : draft.texts()) {
            if (ADVICE.matcher(LintText.unquoted(text.value(), ctx.input())).find()) {
                out.add(new Violation(code(), text.field()));
            }
        }
    }
}
