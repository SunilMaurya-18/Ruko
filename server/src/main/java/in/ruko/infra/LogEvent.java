package in.ruko.infra;

public enum LogEvent {
    ANALYZED,
    REQUEST_REJECTED,
    RATE_LIMITED,
    UNHANDLED_ERROR,
    LINT_FAILED,
    LLM_FALLBACK,
    TTS_FALLBACK,
    ASR_FAILED,
    READINESS_FAILED
}
