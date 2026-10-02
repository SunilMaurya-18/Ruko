package in.ruko.guardrail;

/** {@code field} is a response path such as {@code cards[0].text}; it never carries the offending text. */
public record Violation(LintCode code, String field) {
}
