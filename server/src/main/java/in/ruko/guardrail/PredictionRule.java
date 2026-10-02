package in.ruko.guardrail;

import java.util.List;
import java.util.regex.Pattern;

/** L7: no prices, targets, percentages, or return predictions in Ruko's own words. Quotes from the input are fine. */
final class PredictionRule implements LintRule {

    private static final List<Pattern> PREDICTION = List.of(
            Pattern.compile("[0-9]+(?:[.,][0-9]+)?\\s?%"),
            Pattern.compile("(?:₹|(?<![\\p{L}\\p{N}])(?:rs\\.?|inr))\\s?[0-9]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[0-9][0-9,]*\\s?(?:rupees|rupaye|रुपये|रुपए|रुपया)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?<![\\p{L}\\p{N}])[0-9]+\\s?x(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE),
            LintText.words(List.of(
                    "target", "targets", "target price", "multibagger", "will rise", "will go up", "will double",
                    "will triple", "will jump", "will reach", "will hit", "will touch", "will cross", "will give",
                    "double your money", "returns of", "upar jayega", "badhega", "double hoga", "dugna",
                    "टारगेट", "लक्ष्य", "बढ़ेगा", "चढ़ेगा", "ऊपर जाएगा", "दोगुना", "तिगुना", "डबल होगा")));

    @Override
    public LintCode code() {
        return LintCode.L7;
    }

    @Override
    public void check(Draft draft, LintContext ctx, List<Violation> out) {
        for (Text text : draft.texts()) {
            String own = LintText.unquoted(text.value(), ctx.input());
            if (PREDICTION.stream().anyMatch(pattern -> pattern.matcher(own).find())) {
                out.add(new Violation(code(), text.field()));
            }
        }
    }
}
