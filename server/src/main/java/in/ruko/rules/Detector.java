package in.ruko.rules;

import java.util.List;

/** Finds where a rule (or marker) fires. Evidence ranges are in masked-text coordinates. */
interface Detector {

    Detector NONE = text -> List.of();

    record Found(int start, int end, String item) {
    }

    enum Scope { SENTENCE, MESSAGE }

    record Condition(SpanMatcher matcher, Scope scope) {
    }

    List<Found> find(RuleText text);
}
