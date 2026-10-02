package in.ruko.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.ruko.api.dto.AnalyzeRequest;
import in.ruko.api.dto.AnalyzeResponse;
import in.ruko.infra.config.FeatureFlags;
import in.ruko.pipeline.Lang;
import in.ruko.pipeline.Source;
import in.ruko.rules.Band;
import in.ruko.support.Pipeline;
import org.junit.jupiter.api.Test;

class SnapshotTest {

    private static final String TEXT = "Our SEBI registered investment adviser INA000098765 offers portfolio reviews.";

    private static AnalyzeResponse analyze(SebiSnapshotIndex index) {
        return Pipeline.service(index).analyze(new AnalyzeRequest(TEXT, Lang.EN, Source.SHARE, null));
    }

    private static AnalyzeResponse.Unverified u14(AnalyzeResponse response) {
        assertThat(response.unverified()).hasSize(1);
        AnalyzeResponse.Unverified u14 = response.unverified().getFirst();
        assertThat(u14.id()).isEqualTo("U14");
        assertThat(u14.item()).isEqualTo("INA000098765");
        assertThat(u14.action()).isEqualTo("sebi_check");
        return u14;
    }

    @Test
    void listedNumberIsMarkedListedAndLeavesTheBandAlone() {
        SebiSnapshotIndex index = SebiSnapshotIndex.parse(
                "{\"snapshot_date\": \"2026-09-30\", \"registration_numbers\": [\"INA000098765\", \"INH000067890\"]}");
        AnalyzeResponse response = analyze(index);

        assertThat(u14(response).snapshot()).isEqualTo(SnapshotStatus.LISTED);
        assertThat(response.signals()).isEmpty();
        assertThat(response.band()).isEqualTo(Band.FEW_FLAGS_STILL_VERIFY);
    }

    @Test
    void numberAbsentFromADatedSnapshotRaisesS19WithTheDate() {
        SebiSnapshotIndex index = SebiSnapshotIndex.parse(
                "{\"snapshot_date\": \"2026-09-30\", \"registration_numbers\": [\"INH000067890\"]}");
        AnalyzeResponse response = analyze(index);

        assertThat(u14(response).snapshot()).isEqualTo(SnapshotStatus.NOT_LISTED);
        assertThat(response.signals()).singleElement().satisfies(s19 -> {
            assertThat(s19.id()).isEqualTo("S19");
            assertThat(s19.evidence()).isEqualTo("INA000098765");
            assertThat(s19.reason()).contains("2026-09-30").doesNotContain("{date}");
        });
        assertThat(response.band()).isEqualTo(Band.SOME_CONCERN);
    }

    @Test
    void emptySnapshotFileGivesU14OnlyWithNoSnapshotField() {
        SebiSnapshotIndex index = SebiSnapshotIndex.parse("{\"snapshot_date\": null, \"registration_numbers\": []}");
        AnalyzeResponse response = analyze(index);

        assertThat(index.active()).isFalse();
        assertThat(u14(response).snapshot()).isNull();
        assertThat(response.signals()).isEmpty();
        assertThat(response.band()).isEqualTo(Band.FEW_FLAGS_STILL_VERIFY);
    }

    @Test
    void numbersWithoutADateAreIgnored() {
        SebiSnapshotIndex index = SebiSnapshotIndex.parse("{\"registration_numbers\": [\"INH000067890\"]}");
        AnalyzeResponse response = analyze(index);

        assertThat(index.active()).isFalse();
        assertThat(index.date()).isEmpty();
        assertThat(u14(response).snapshot()).isNull();
        assertThat(response.signals()).isEmpty();
    }

    @Test
    void shippedSnapshotIsTheEmptyFile() {
        SebiSnapshotIndex shipped = Pipeline.shippedSnapshot();
        assertThat(shipped.active()).isFalse();
        assertThat(shipped.size()).isZero();
        assertThat(u14(analyze(shipped)).snapshot()).isNull();
    }

    @Test
    void snapshotFlagOffMeansNoSnapshot() {
        SebiSnapshotIndex off = new SebiSnapshotIndex(new FeatureFlags(false, true, false, false));
        assertThat(off.active()).isFalse();
        assertThat(off.lookup("INA000098765")).isEmpty();
    }

    @Test
    void malformedFileAbortsBoot() {
        assertThatThrownBy(() -> SebiSnapshotIndex.parse(
                "{\"snapshot_date\": \"2026-09-30\", \"registration_numbers\": [\"INA12345\"]}"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("malformed registration number");
        assertThatThrownBy(() -> SebiSnapshotIndex.parse(
                "{\"snapshot_date\": \"30/09/2026\", \"registration_numbers\": [\"INA000098765\"]}"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("YYYY-MM-DD");
        assertThatThrownBy(() -> SebiSnapshotIndex.parse(
                "{\"snapshot_date\": \"2026-09-30\", \"registration_numbers\": [], \"names\": []}"))
                .isInstanceOf(IllegalStateException.class);
    }
}
