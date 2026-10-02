package in.ruko.guardrail;

import in.ruko.api.dto.AnalyzeResponse;
import java.util.List;
import java.util.Objects;

/**
 * L8: the draft says what the rules said. Band and content class must match; the analogy may be dropped but not
 * swapped; every signal, card, and item must belong to a fired signal, with the catalogue severity. This is what
 * stops a model from relabelling a promotion as education or a high concern as a clearance.
 */
final class RuleResultRule implements LintRule {

    @Override
    public LintCode code() {
        return LintCode.L8;
    }

    @Override
    public void check(Draft draft, LintContext ctx, List<Violation> out) {
        AnalyzeResponse response = draft.response();
        if (response.band() != ctx.band()) {
            out.add(new Violation(code(), "band"));
        }
        if (response.contentClass() != ctx.contentClass()) {
            out.add(new Violation(code(), "content_class"));
        }
        if (response.analogyKey() != null && !Objects.equals(response.analogyKey(), ctx.analogyKey())) {
            out.add(new Violation(code(), "analogy_key"));
        }
        for (int i = 0; i < response.signals().size(); i++) {
            AnalyzeResponse.Signal signal = response.signals().get(i);
            if (signal.severity() != ctx.hits().get(signal.id())) {
                out.add(new Violation(code(), "signals[" + i + "]"));
            }
        }
        for (int i = 0; i < response.unverified().size(); i++) {
            if (!ctx.hits().containsKey(response.unverified().get(i).id())) {
                out.add(new Violation(code(), "unverified[" + i + "]"));
            }
        }
        for (int i = 0; i < response.reassuring().size(); i++) {
            if (!ctx.hits().containsKey(response.reassuring().get(i).id())) {
                out.add(new Violation(code(), "reassuring[" + i + "]"));
            }
        }
        for (int i = 0; i < response.cards().size(); i++) {
            String id = response.cards().get(i).signalId();
            if (id != null && !ctx.hits().containsKey(id)) {
                out.add(new Violation(code(), "cards[" + i + "].signal_id"));
            }
        }
    }
}
