package in.ruko.explain;

import in.ruko.api.dto.AnalyzeResponse;
import in.ruko.content.I18nBundle;
import in.ruko.pipeline.Engine;
import in.ruko.rules.RuleLoader;
import in.ruko.rules.RuleSet;
import in.ruko.rules.Severity;
import in.ruko.rules.SignalHit;
import in.ruko.rules.SignalRule;
import in.ruko.snapshot.SebiSnapshotIndex;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Builds the response purely from catalogue keys: reasons and one card per fired signal id, in engine order.
 * Assist card text, when given, replaces the template card for that id; everything else stays catalogue text.
 */
@Component
public class TemplateExplainer implements ExplainerPort {

    private final RuleSet rules;
    private final I18nBundle i18n;
    private final SebiSnapshotIndex snapshot;

    public TemplateExplainer(RuleLoader rules, I18nBundle i18n, SebiSnapshotIndex snapshot) {
        this.rules = rules.ruleSet();
        this.i18n = i18n;
        this.snapshot = snapshot;
    }

    @Override
    public AnalyzeResponse compose(ExplainInput in, Assist assist) {
        Map<String, String> params = Map.of("date", snapshot.date().map(Object::toString).orElse(""));
        List<AnalyzeResponse.Signal> signals = new ArrayList<>();
        List<AnalyzeResponse.Unverified> unverified = new ArrayList<>();
        List<AnalyzeResponse.Reassuring> reassuring = new ArrayList<>();
        List<AnalyzeResponse.Card> cards = new ArrayList<>();
        Set<String> carded = new HashSet<>();
        boolean assisted = false;
        for (SignalHit hit : in.hits()) {
            SignalRule rule = rules.require(hit.id());
            if (hit.severity().countsTowardBand()) {
                signals.add(new AnalyzeResponse.Signal(hit.id(), hit.severity(), hit.evidence(),
                        i18n.text(in.lang(), rule.reasonKey(), params)));
            } else if (hit.severity() == Severity.UNVERIFIED) {
                unverified.add(new AnalyzeResponse.Unverified(hit.id(), hit.item(), rule.action(), hit.snapshot()));
            } else {
                reassuring.add(new AnalyzeResponse.Reassuring(hit.id(), hit.evidence(),
                        i18n.text(in.lang(), rule.reasonKey(), params)));
            }
            if (carded.add(hit.id())) {
                String text = assist.cards().get(hit.id());
                assisted |= text != null;
                cards.add(new AnalyzeResponse.Card(hit.id(),
                        text != null ? text : i18n.text(in.lang(), rule.cardKey(), params)));
            }
        }
        Engine engine = assisted || in.llmTagged() ? Engine.LLM : Engine.TEMPLATE;
        return new AnalyzeResponse(in.lang(), in.entities(), signals, unverified, reassuring, in.band(),
                in.contentClass(), new AnalyzeResponse.Counts(signals.size(), unverified.size(), reassuring.size()),
                cards, in.analogyKey(), AnalyzeResponse.FOOTER_KEY, engine);
    }
}
