package in.ruko.explain;

import in.ruko.rules.RuleLoader;
import in.ruko.rules.RuleSet;
import in.ruko.rules.RuleText;
import in.ruko.rules.SignalHit;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Picks at most one analogy key: from the highest-severity hit whose catalogue row has one, else from an analogy
 * trigger (F&O or leverage wording). No key means no analogy; nothing here can invent one.
 */
@Component
public class AnalogyCatalog {

    private final RuleSet rules;

    public AnalogyCatalog(RuleLoader loader) {
        this.rules = loader.ruleSet();
    }

    /** {@code hits} must be in engine order (severity, then catalogue order). */
    public Optional<String> select(RuleText text, List<SignalHit> hits) {
        for (SignalHit hit : hits) {
            String key = rules.require(hit.id()).analogyKey();
            if (key != null && hit.severity().countsTowardBand()) {
                return Optional.of(key);
            }
        }
        return rules.analogyTrigger(text);
    }
}
