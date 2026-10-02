package in.ruko.guardrail;

import in.ruko.api.dto.AnalyzeResponse;
import java.util.List;

/** One linter check. Rules add violations; they never change the draft. */
public interface LintRule {

    LintCode code();

    void check(Draft draft, LintContext ctx, List<Violation> out);

    /** The free text Ruko wrote (reasons, cards, analogy). Evidence is input, checked by L4 instead. */
    record Text(String field, Kind kind, String value) {
    }

    /** {@code PLAIN} is text written outside an analysis, such as the complaint draft. */
    enum Kind { REASON, CARD, ANALOGY, PLAIN }

    /** {@code response} is null for plain text. */
    record Draft(AnalyzeResponse response, List<Text> texts) {
    }
}
