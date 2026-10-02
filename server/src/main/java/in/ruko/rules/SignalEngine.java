package in.ruko.rules;

import in.ruko.pipeline.AnalysisContext;
import in.ruko.snapshot.SebiSnapshotIndex;
import in.ruko.snapshot.SnapshotStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Runs every enabled text rule over the masked text, then the snapshot look-up. Rules with an {@code unless}
 * on severities run after the rest. One hit per signal id, except rules with an action (U14), which get one per
 * distinct item. Hits are ordered by severity, then catalogue order.
 */
@Component
public class SignalEngine {

    private static final String SNAPSHOT_DETECTOR = "snapshot";

    private final RuleSet rules;
    private final SebiSnapshotIndex snapshot;

    @Autowired
    public SignalEngine(RuleLoader loader, SebiSnapshotIndex snapshot) {
        this(loader.ruleSet(), snapshot);
    }

    SignalEngine(RuleSet rules, SebiSnapshotIndex snapshot) {
        this.rules = rules;
        this.snapshot = snapshot;
    }

    public List<SignalHit> evaluate(AnalysisContext ctx) {
        return evaluate(RuleText.of(ctx));
    }

    public List<SignalHit> evaluate(RuleText text) {
        List<SignalHit> hits = new ArrayList<>();
        Map<String, Boolean> markers = new HashMap<>();
        List<SignalRule> deferred = new ArrayList<>();
        for (SignalRule rule : rules.rules()) {
            if (!rule.textRule()) {
                continue;
            }
            if (rule.unlessSeverities().isEmpty()) {
                run(rule, text, hits, markers);
            } else {
                deferred.add(rule);
            }
        }
        for (SignalRule rule : deferred) {
            run(rule, text, hits, markers);
        }
        applySnapshot(hits);
        hits.sort(Comparator.comparing(SignalHit::severity)
                .thenComparingInt(hit -> rules.require(hit.id()).order()));
        return List.copyOf(hits);
    }

    private void run(SignalRule rule, RuleText text, List<SignalHit> hits, Map<String, Boolean> markers) {
        for (String marker : rule.unlessMarkers()) {
            if (markers.computeIfAbsent(marker, name -> rules.marker(name, text))) {
                return;
            }
        }
        if (hits.stream().anyMatch(hit -> rule.unlessSeverities().contains(hit.severity()))) {
            return;
        }
        List<Detector.Found> found = rule.find(text);
        if (found.isEmpty()) {
            return;
        }
        if (rule.action() == null) {
            hits.add(hit(rule, text, found.getFirst()));
            return;
        }
        Set<String> items = new LinkedHashSet<>();
        for (Detector.Found one : found) {
            if (one.item() != null && items.add(one.item())) {
                hits.add(hit(rule, text, one));
            }
        }
    }

    private static SignalHit hit(SignalRule rule, RuleText text, Detector.Found found) {
        String item = rule.action() == null ? null : found.item();
        return new SignalHit(rule.id(), rule.severity(), text.quote(found.start(), found.end()), item, null);
    }

    /** Listed leaves the band alone; not listed adds S19 only for a dated, non-empty snapshot. */
    private void applySnapshot(List<SignalHit> hits) {
        Optional<SignalRule> absent = rules.emittedBy(SNAPSHOT_DETECTOR);
        List<SignalHit> added = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            SignalHit hit = hits.get(i);
            if (rules.require(hit.id()).action() == null || hit.item() == null) {
                continue;
            }
            Optional<SnapshotStatus> status = snapshot.lookup(hit.item());
            if (status.isEmpty()) {
                continue;
            }
            hits.set(i, hit.withSnapshot(status.get()));
            if (status.get() == SnapshotStatus.NOT_LISTED && absent.isPresent()) {
                SignalRule s19 = absent.get();
                added.add(new SignalHit(s19.id(), s19.severity(), hit.evidence(), hit.item(), null));
            }
        }
        hits.addAll(added);
    }
}
