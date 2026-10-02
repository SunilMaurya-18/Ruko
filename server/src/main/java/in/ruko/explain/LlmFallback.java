package in.ruko.explain;

/** Why an assist was dropped. Logged as a tag; never with the reply. */
public enum LlmFallback {
    CIRCUIT_OPEN,
    TIMEOUT,
    ERROR,
    SCHEMA,
    TAG,
    SPAN,
    CARD
}
