package in.ruko.rules;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** The compiled catalogue: signals in file order, named markers, and analogy triggers. */
public final class RuleSet {

    public static final String PAYMENT_ASK = "payment_ask";
    public static final String EXPLANATION = "explanation";

    record AnalogyTrigger(String analogyKey, SpanMatcher matcher) {
    }

    private final List<SignalRule> rules;
    private final Map<String, SignalRule> byId = new LinkedHashMap<>();
    private final Map<String, Detector> markers;
    private final List<AnalogyTrigger> analogyTriggers;

    RuleSet(List<SignalRule> rules, Map<String, Detector> markers, List<AnalogyTrigger> analogyTriggers) {
        this.rules = List.copyOf(rules);
        rules.forEach(rule -> byId.put(rule.id(), rule));
        this.markers = Map.copyOf(markers);
        this.analogyTriggers = List.copyOf(analogyTriggers);
    }

    public List<SignalRule> rules() {
        return rules;
    }

    public Optional<SignalRule> rule(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public SignalRule require(String id) {
        return rule(id).orElseThrow(() -> new IllegalArgumentException("unknown signal " + id));
    }

    Optional<SignalRule> emittedBy(String detector) {
        return rules.stream().filter(rule -> rule.enabled() && detector.equals(rule.detector())).findFirst();
    }

    public boolean marker(String name, RuleText text) {
        Detector marker = markers.get(name);
        if (marker == null) {
            throw new IllegalArgumentException("unknown marker " + name);
        }
        return !marker.find(text).isEmpty();
    }

    /** Every analogy key the catalogue can select, from signal rows and triggers. */
    public Set<String> analogyKeys() {
        Set<String> keys = new LinkedHashSet<>();
        rules.stream().map(SignalRule::analogyKey).filter(Objects::nonNull).forEach(keys::add);
        analogyTriggers.forEach(trigger -> keys.add(trigger.analogyKey()));
        return keys;
    }

    public Optional<String> analogyTrigger(RuleText text) {
        return analogyTriggers.stream()
                .filter(trigger -> !trigger.matcher().find(text).isEmpty())
                .map(AnalogyTrigger::analogyKey)
                .findFirst();
    }
}
