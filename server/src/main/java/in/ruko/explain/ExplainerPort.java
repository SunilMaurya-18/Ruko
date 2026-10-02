package in.ruko.explain;

import in.ruko.api.dto.AnalyzeResponse;

/** Builds the response text for a rule result. It may word things; it never changes band, class, or severity. */
public interface ExplainerPort {

    AnalyzeResponse compose(ExplainInput input, Assist assist);

    default AnalyzeResponse compose(ExplainInput input) {
        return compose(input, Assist.EMPTY);
    }
}
