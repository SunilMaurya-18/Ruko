package in.ruko.rules;

import java.util.List;
import java.util.Set;

/**
 * One catalogue row from {@code signals.v0.json}, validated and compiled. {@code detector} names an external
 * source that emits the signal (S19: the snapshot look-up); such rules are not run by the text engine.
 * Severity always comes from here, never from a model.
 */
public record SignalRule(
        String id,
        Severity severity,
        RuleType type,
        boolean enabled,
        boolean llmTag,
        String reasonKey,
        String spokenKey,
        String analogyKey,
        String action,
        String detector,
        Set<String> unlessMarkers,
        Set<Severity> unlessSeverities,
        int order,
        Detector matcher) {

    public boolean textRule() {
        return enabled && detector == null;
    }

    List<Detector.Found> find(RuleText text) {
        return matcher.find(text);
    }
}
