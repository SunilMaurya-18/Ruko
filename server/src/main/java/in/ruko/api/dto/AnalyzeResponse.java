package in.ruko.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.ruko.extract.Entities;
import in.ruko.pipeline.Engine;
import in.ruko.pipeline.Lang;
import in.ruko.rules.Band;
import in.ruko.rules.ContentClass;
import in.ruko.rules.Severity;
import in.ruko.snapshot.SnapshotStatus;
import java.util.List;

/** Response shape from TRD §5. */
public record AnalyzeResponse(
        Lang language,
        Entities entities,
        List<Signal> signals,
        List<Unverified> unverified,
        List<Reassuring> reassuring,
        Band band,
        ContentClass contentClass,
        Counts counts,
        List<Card> cards,
        String analogyKey,
        String footerKey,
        Engine engine) {

    public static final String FOOTER_KEY = "no_flags_not_safe";

    /** Critical, strong, and moderate hits: the ones that count toward the band. */
    public record Signal(String id, Severity severity, String evidence, String reason) {
    }

    /** {@code snapshot} is omitted when no dated snapshot was consulted. */
    public record Unverified(String id, String item, String action,
                             @JsonInclude(JsonInclude.Include.NON_NULL) SnapshotStatus snapshot) {
    }

    public record Reassuring(String id, String evidence, String reason) {
    }

    public record Counts(int redFlags, int couldntVerify, int reassuring) {
    }

    public record Card(String signalId, String text) {
    }
}
