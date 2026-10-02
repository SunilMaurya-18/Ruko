package in.ruko.content;

import in.ruko.pipeline.Lang;
import in.ruko.rules.Band;
import in.ruko.rules.RuleLoader;
import in.ruko.rules.RuleSet;
import in.ruko.rules.Severity;
import in.ruko.rules.SignalRule;
import in.ruko.snapshot.SebiSnapshotIndex;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The public "How Ruko decides" page: signal ids with plain reasons, the bands, the limits, and the snapshot date.
 * Built from catalogue keys only. No patterns, terms, or rate-limit numbers appear here.
 */
@Component
public class HowRukoDecides {

    private final RuleSet rules;
    private final I18nBundle i18n;
    private final SebiSnapshotIndex snapshot;

    public HowRukoDecides(RuleLoader rules, I18nBundle i18n, SebiSnapshotIndex snapshot) {
        this.rules = rules.ruleSet();
        this.i18n = i18n;
        this.snapshot = snapshot;
    }

    public record Page(Lang language, String title, String intro, List<BandLine> bands, String bandsNote,
                       List<SignalLine> signals, List<String> limits, LocalDate snapshotDate, String snapshotNote) {
    }

    public record BandLine(Band band, String label) {
    }

    public record SignalLine(String id, Severity severity, String reason) {
    }

    public Page page(Lang lang) {
        LocalDate date = snapshot.date().orElse(null);
        Map<String, String> params = Map.of("date", date == null ? "" : date.toString());

        List<BandLine> bands = new ArrayList<>();
        for (Band band : Band.values()) {
            bands.add(new BandLine(band, i18n.text(lang, "band." + band.wire())));
        }
        List<SignalLine> signals = new ArrayList<>();
        for (SignalRule rule : rules.rules()) {
            if (canFire(rule)) {
                signals.add(new SignalLine(rule.id(), rule.severity(), i18n.text(lang, rule.reasonKey(), params)));
            }
        }
        List<String> limits = new ArrayList<>();
        for (int i = 1; i18n.has(lang, "how.limit." + i); i++) {
            limits.add(i18n.text(lang, "how.limit." + i));
        }
        String snapshotNote = date == null
                ? i18n.text(lang, "how.snapshot.none")
                : i18n.text(lang, "how.snapshot.dated", params);
        return new Page(lang, i18n.text(lang, "how.title"), i18n.text(lang, "how.intro"), bands,
                i18n.text(lang, "how.bands"), signals, limits, date, snapshotNote);
    }

    /** Disabled rules, and S19 without a dated snapshot, cannot fire, so they are not listed. */
    private boolean canFire(SignalRule rule) {
        return rule.enabled() && (rule.detector() == null || snapshot.active());
    }
}
