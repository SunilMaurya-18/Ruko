package in.ruko.guardrail;

import in.ruko.pipeline.Lang;
import java.util.List;

/**
 * L6: Hindi output is mostly Devanagari (names such as SEBI, UPI, or Play Store may stay in Latin script); English
 * output has no Devanagari at all.
 */
final class LanguageRule implements LintRule {

    @Override
    public LintCode code() {
        return LintCode.L6;
    }

    @Override
    public void check(Draft draft, LintContext ctx, List<Violation> out) {
        for (Text text : draft.texts()) {
            int devanagari = 0;
            int latin = 0;
            String value = text.value();
            for (int i = 0; i < value.length(); ) {
                int cp = value.codePointAt(i);
                i += Character.charCount(cp);
                if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.DEVANAGARI && !Character.isDigit(cp)) {
                    devanagari++;
                } else if ((cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z')) {
                    latin++;
                }
            }
            boolean ok = ctx.lang() == Lang.HI ? devanagari > 0 && devanagari >= latin : devanagari == 0;
            if (!ok) {
                out.add(new Violation(code(), text.field()));
            }
        }
    }
}
