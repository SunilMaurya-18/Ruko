package in.ruko.guardrail;

import in.ruko.api.dto.AnalyzeResponse;
import in.ruko.content.I18nBundle;
import in.ruko.guardrail.LintRule.Draft;
import in.ruko.guardrail.LintRule.Kind;
import in.ruko.guardrail.LintRule.Text;
import in.ruko.pipeline.Lang;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Runs L1–L8 over a draft response before it leaves the server. The analogy is linted as the catalogue text its key
 * resolves to. The caller fails closed on any violation (template draft, then {@link FallbackTemplates}).
 */
@Component
public final class GuardrailLinter {

    private final I18nBundle i18n;
    private final List<LintRule> rules;
    private final List<LintRule> plainTextRules;

    public GuardrailLinter(I18nBundle i18n, OutboundLinkPolicy links, TickerSnapshot tickers) {
        this.i18n = i18n;
        RecommendationRule advice = new RecommendationRule();
        VerdictRule verdicts = new VerdictRule();
        LinkRule linkRule = new LinkRule(links);
        this.rules = List.of(advice, new TickerRule(tickers), verdicts, new LengthRule(), linkRule,
                new LanguageRule(), new PredictionRule(), new RuleResultRule());
        this.plainTextRules = List.of(advice, verdicts, linkRule);
    }

    /**
     * L1, L3, and L5 over text Ruko writes outside an analysis (the complaint draft). The other checks need a rule
     * result, or would flag the amount the user entered themselves.
     */
    public LintResult lintPlainText(Lang lang, String field, String text) {
        Draft draft = new Draft(null, List.of(new Text(field, Kind.PLAIN, text)));
        LintContext ctx = new LintContext(lang, "", null, null, null, Map.of());
        List<Violation> violations = new ArrayList<>();
        for (LintRule rule : plainTextRules) {
            rule.check(draft, ctx, violations);
        }
        return new LintResult(violations);
    }

    public static int wordCount(String text) {
        return LintText.wordCount(text);
    }

    public LintResult lint(AnalyzeResponse response, LintContext ctx) {
        Draft draft = new Draft(response, texts(response, ctx));
        List<Violation> violations = new ArrayList<>();
        for (LintRule rule : rules) {
            rule.check(draft, ctx, violations);
        }
        return new LintResult(violations);
    }

    private List<Text> texts(AnalyzeResponse response, LintContext ctx) {
        List<Text> texts = new ArrayList<>();
        for (int i = 0; i < response.signals().size(); i++) {
            texts.add(new Text("signals[" + i + "].reason", Kind.REASON, orEmpty(response.signals().get(i).reason())));
        }
        for (int i = 0; i < response.reassuring().size(); i++) {
            texts.add(new Text("reassuring[" + i + "].reason", Kind.REASON,
                    orEmpty(response.reassuring().get(i).reason())));
        }
        for (int i = 0; i < response.cards().size(); i++) {
            texts.add(new Text("cards[" + i + "].text", Kind.CARD, orEmpty(response.cards().get(i).text())));
        }
        String analogyKey = response.analogyKey();
        if (analogyKey != null) {
            String analogy = i18n.has(ctx.lang(), analogyKey) ? i18n.text(ctx.lang(), analogyKey) : "";
            texts.add(new Text("analogy_key", Kind.ANALOGY, analogy));
        }
        return texts;
    }

    private static String orEmpty(String text) {
        return text == null ? "" : text;
    }
}
