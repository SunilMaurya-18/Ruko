package in.ruko.extract;

/** One extracted entity: its type, its span in the masked text, and its reported value. */
public record EntityMatch(String type, int start, int end, String value) {
}
