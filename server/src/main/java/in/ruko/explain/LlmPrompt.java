package in.ruko.explain;

/** One chat request: fixed instructions, and the message inside a delimited data block. Never logged. */
public record LlmPrompt(String system, String user) {

    @Override
    public String toString() {
        return "LlmPrompt[redacted]";
    }
}
