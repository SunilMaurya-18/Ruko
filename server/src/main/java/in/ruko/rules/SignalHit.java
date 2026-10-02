package in.ruko.rules;

import in.ruko.snapshot.SnapshotStatus;

/**
 * One fired signal. {@code evidence} is quoted verbatim from the request text, with masked personal data shown as
 * its placeholder. {@code item} is set only for rules with an action (U14: the registration number) and for S19;
 * {@code snapshot} is set only on U14 when a dated snapshot was consulted.
 */
public record SignalHit(String id, Severity severity, String evidence, String item, SnapshotStatus snapshot) {

    public SignalHit withSnapshot(SnapshotStatus status) {
        return new SignalHit(id, severity, evidence, item, status);
    }
}
