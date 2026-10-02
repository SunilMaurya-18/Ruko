package in.ruko.guardrail;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record LintResult(List<Violation> violations) {

    public LintResult {
        violations = List.copyOf(violations);
    }

    public boolean ok() {
        return violations.isEmpty();
    }

    public Set<LintCode> codes() {
        Set<LintCode> codes = EnumSet.noneOf(LintCode.class);
        violations.forEach(violation -> codes.add(violation.code()));
        return codes;
    }
}
