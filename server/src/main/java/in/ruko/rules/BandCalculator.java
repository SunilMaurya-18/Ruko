package in.ruko.rules;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The band rule from TRD §2, verbatim: distinct signal ids, counts not percentages. Reads hits only, so nothing a
 * model writes can move it. UNVERIFIED and REASSURANCE never count, so R1 and R2 cannot lower the band.
 */
public final class BandCalculator {

    private BandCalculator() {
    }

    public static Band band(Collection<SignalHit> hits, boolean unreadable) {
        if (unreadable) {
            return Band.NOT_ENOUGH_TO_JUDGE;
        }
        Map<Severity, Long> n = hits.stream()
                .map(h -> Map.entry(h.id(), h.severity())).distinct()
                .collect(Collectors.groupingBy(Map.Entry::getValue, Collectors.counting()));
        long c = n.getOrDefault(Severity.CRITICAL, 0L);
        long s = n.getOrDefault(Severity.STRONG, 0L);
        long m = n.getOrDefault(Severity.MODERATE, 0L);
        if (c >= 1 || s >= 2) {
            return Band.HIGH_CONCERN;
        }
        if (s == 1 || m >= 2) {
            return Band.SOME_CONCERN;
        }
        return Band.FEW_FLAGS_STILL_VERIFY;
    }
}
