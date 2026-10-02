package in.ruko.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text derived from an original string by a series of edits, keeping for every char the range of the
 * original it came from. Any span found in the derived text can be quoted verbatim from the original.
 */
public final class MappedText {

    private final String original;
    private final String value;
    private final int[] starts;
    private final int[] ends;

    private MappedText(String original, String value, int[] starts, int[] ends) {
        this.original = original;
        this.value = value;
        this.starts = starts;
        this.ends = ends;
    }

    public static MappedText of(String original) {
        int length = original.length();
        int[] starts = new int[length];
        int[] ends = new int[length];
        for (int i = 0; i < length; i++) {
            starts[i] = i;
            ends[i] = i + 1;
        }
        return new MappedText(original, original, starts, ends);
    }

    public String original() {
        return original;
    }

    public String value() {
        return value;
    }

    public int length() {
        return value.length();
    }

    /** Verbatim slice of the original that produced {@code value[start, end)}. */
    public String originalSlice(int start, int end) {
        if (start >= end) {
            return "";
        }
        return original.substring(starts[start], ends[end - 1]);
    }

    /** Replacement {@code start..end} must be sorted and non-overlapping, in coordinates of {@link #value()}. */
    public record Edit(int start, int end, String replacement) {
    }

    public MappedText apply(List<Edit> edits) {
        if (edits.isEmpty()) {
            return this;
        }
        int newLength = value.length();
        for (Edit edit : edits) {
            newLength += edit.replacement().length() - (edit.end() - edit.start());
        }
        StringBuilder out = new StringBuilder(newLength);
        int[] newStarts = new int[newLength];
        int[] newEnds = new int[newLength];
        int cursor = 0;
        int written = 0;
        for (Edit edit : edits) {
            if (edit.start() < cursor || edit.end() < edit.start() || edit.end() > value.length()) {
                throw new IllegalArgumentException("edits must be sorted, non-overlapping, and in range");
            }
            for (int i = cursor; i < edit.start(); i++, written++) {
                out.append(value.charAt(i));
                newStarts[written] = starts[i];
                newEnds[written] = ends[i];
            }
            int originalStart;
            int originalEnd;
            if (edit.start() < edit.end()) {
                originalStart = starts[edit.start()];
                originalEnd = ends[edit.end() - 1];
            } else {
                originalStart = edit.start() < value.length() ? starts[edit.start()] : original.length();
                originalEnd = originalStart;
            }
            for (int i = 0; i < edit.replacement().length(); i++, written++) {
                out.append(edit.replacement().charAt(i));
                newStarts[written] = originalStart;
                newEnds[written] = originalEnd;
            }
            cursor = edit.end();
        }
        for (int i = cursor; i < value.length(); i++, written++) {
            out.append(value.charAt(i));
            newStarts[written] = starts[i];
            newEnds[written] = ends[i];
        }
        return new MappedText(original, out.toString(), newStarts, newEnds);
    }

    /** Replaces every match whose replacement differs from the matched text. */
    public MappedText replaceAll(Pattern pattern, Function<MatchResult, String> replacer) {
        Matcher matcher = pattern.matcher(value);
        List<Edit> edits = new ArrayList<>();
        while (matcher.find()) {
            String replacement = replacer.apply(matcher.toMatchResult());
            if (!replacement.contentEquals(value.subSequence(matcher.start(), matcher.end()))) {
                edits.add(new Edit(matcher.start(), matcher.end(), replacement));
            }
        }
        return apply(edits);
    }
}
