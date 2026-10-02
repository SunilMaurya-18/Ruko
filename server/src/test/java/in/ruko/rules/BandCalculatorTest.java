package in.ruko.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class BandCalculatorTest {

    private static SignalHit hit(String id) {
        Severity severity = switch (id.charAt(0)) {
            case 'C' -> Severity.CRITICAL;
            case 'S' -> Severity.STRONG;
            case 'M' -> Severity.MODERATE;
            case 'U' -> Severity.UNVERIFIED;
            default -> Severity.REASSURANCE;
        };
        return new SignalHit(id, severity, "evidence", null, null);
    }

    private static List<SignalHit> hits(String... ids) {
        return Stream.of(ids).map(BandCalculatorTest::hit).toList();
    }

    static Stream<Arguments> table() {
        return Stream.of(
                Arguments.of("nothing", List.of(), false, Band.FEW_FLAGS_STILL_VERIFY),
                Arguments.of("one critical", List.of("C2"), false, Band.HIGH_CONCERN),
                Arguments.of("two strong", List.of("S5", "S7"), false, Band.HIGH_CONCERN),
                Arguments.of("one strong", List.of("S6"), false, Band.SOME_CONCERN),
                Arguments.of("two moderate", List.of("M11", "M13"), false, Band.SOME_CONCERN),
                Arguments.of("three moderate", List.of("M11", "M12", "M13"), false, Band.SOME_CONCERN),
                Arguments.of("one moderate", List.of("M11"), false, Band.FEW_FLAGS_STILL_VERIFY),
                Arguments.of("strong plus moderate", List.of("S9", "M11"), false, Band.SOME_CONCERN),
                Arguments.of("same strong twice counts once", List.of("S5", "S5"), false, Band.SOME_CONCERN),
                Arguments.of("same moderate twice counts once", List.of("M11", "M11"), false, Band.FEW_FLAGS_STILL_VERIFY),
                Arguments.of("S19 per number still counts once", List.of("S19", "S19"), false, Band.SOME_CONCERN),
                Arguments.of("U14 alone", List.of("U14"), false, Band.FEW_FLAGS_STILL_VERIFY),
                Arguments.of("R1 does not lower critical", List.of("C2", "R1"), false, Band.HIGH_CONCERN),
                Arguments.of("R2 does not lower two strong", List.of("S6", "S7", "R2"), false, Band.HIGH_CONCERN),
                Arguments.of("R1 R2 U14 only", List.of("R1", "R2", "U14"), false, Band.FEW_FLAGS_STILL_VERIFY),
                Arguments.of("unreadable beats critical", List.of("C1", "C3"), true, Band.NOT_ENOUGH_TO_JUDGE),
                Arguments.of("unreadable and empty", List.of(), true, Band.NOT_ENOUGH_TO_JUDGE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    void followsTheTrdBandRule(String name, List<String> ids, boolean unreadable, Band expected) {
        assertThat(BandCalculator.band(hits(ids.toArray(String[]::new)), unreadable)).isEqualTo(expected);
    }

    @Test
    void everySeverityCombinationMatchesTheRule() {
        for (int c = 0; c <= 2; c++) {
            for (int s = 0; s <= 3; s++) {
                for (int m = 0; m <= 3; m++) {
                    List<SignalHit> hits = new ArrayList<>();
                    for (int i = 0; i < c; i++) {
                        hits.add(hit("C" + (i + 1)));
                    }
                    for (int i = 0; i < s; i++) {
                        hits.add(hit("S" + (i + 5)));
                    }
                    for (int i = 0; i < m; i++) {
                        hits.add(hit("M" + (i + 11)));
                    }
                    hits.addAll(hits("U14", "R1", "R2"));
                    Band expected = c >= 1 || s >= 2 ? Band.HIGH_CONCERN
                            : s == 1 || m >= 2 ? Band.SOME_CONCERN : Band.FEW_FLAGS_STILL_VERIFY;
                    assertThat(BandCalculator.band(hits, false)).as("c=%d s=%d m=%d", c, s, m).isEqualTo(expected);
                }
            }
        }
    }

    @Test
    void addingAHitNeverLowersTheBand() {
        List<String> all = List.of("C1", "C2", "S5", "S6", "M11", "M12", "U14", "R1", "R2");
        for (int mask = 0; mask < 1 << all.size(); mask++) {
            List<String> base = new ArrayList<>();
            for (int i = 0; i < all.size(); i++) {
                if ((mask & 1 << i) != 0) {
                    base.add(all.get(i));
                }
            }
            Band before = BandCalculator.band(hits(base.toArray(String[]::new)), false);
            for (String extra : all) {
                List<String> more = new ArrayList<>(base);
                more.add(extra);
                Band after = BandCalculator.band(hits(more.toArray(String[]::new)), false);
                assertThat(after.ordinal()).as("%s + %s", base, extra).isLessThanOrEqualTo(before.ordinal());
            }
        }
    }
}
