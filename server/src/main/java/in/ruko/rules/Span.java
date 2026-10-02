package in.ruko.rules;

/** A range of the masked text; {@code value} is the entity value for entity spans, otherwise {@code null}. */
public record Span(int start, int end, String value) {

    boolean inside(int from, int to) {
        return start >= from && end <= to;
    }

    int distanceTo(Span other) {
        if (end <= other.start) {
            return other.start - end;
        }
        if (other.end <= start) {
            return start - other.end;
        }
        return 0;
    }
}
