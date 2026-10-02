package in.ruko.guardrail;

import in.ruko.api.dto.AnalyzeResponse;
import java.util.List;
import java.util.Locale;

/** L4: cards at most 25 words, the analogy at most 40, and every evidence quote and item present in the input. */
final class LengthRule implements LintRule {

    static final int MAX_CARD_WORDS = 25;
    static final int MAX_ANALOGY_WORDS = 40;

    @Override
    public LintCode code() {
        return LintCode.L4;
    }

    @Override
    public void check(Draft draft, LintContext ctx, List<Violation> out) {
        for (Text text : draft.texts()) {
            int words = LintText.wordCount(text.value());
            int max = switch (text.kind()) {
                case CARD -> MAX_CARD_WORDS;
                case ANALOGY -> MAX_ANALOGY_WORDS;
                case REASON, PLAIN -> Integer.MAX_VALUE;
            };
            if (words == 0 || words > max) {
                out.add(new Violation(code(), text.field()));
            }
        }
        AnalyzeResponse response = draft.response();
        for (int i = 0; i < response.signals().size(); i++) {
            evidence(response.signals().get(i).evidence(), "signals[" + i + "].evidence", ctx, out);
        }
        for (int i = 0; i < response.reassuring().size(); i++) {
            evidence(response.reassuring().get(i).evidence(), "reassuring[" + i + "].evidence", ctx, out);
        }
        String upperInput = ctx.input().toUpperCase(Locale.ROOT);
        for (int i = 0; i < response.unverified().size(); i++) {
            String item = response.unverified().get(i).item();
            if (item == null || item.isBlank() || !upperInput.contains(item)) {
                out.add(new Violation(code(), "unverified[" + i + "].item"));
            }
        }
    }

    private void evidence(String quote, String field, LintContext ctx, List<Violation> out) {
        if (quote == null || quote.isBlank() || !ctx.input().contains(quote)) {
            out.add(new Violation(code(), field));
        }
    }
}
