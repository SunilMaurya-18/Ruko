package in.ruko.rules;

/** A model's claim that signal {@code id} is present, with the span it quotes. No severity: that is the catalogue's. */
public record LlmTag(String id, String span) {
}
