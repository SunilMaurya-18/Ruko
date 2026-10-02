package in.ruko.pipeline;

import in.ruko.pipeline.MappedText.Edit;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * NFKC, strip invisible and control characters, collapse letter-by-letter obfuscation, Devanagari digits to
 * ASCII, case-fold. Every step keeps the offset map so evidence can be quoted from the original.
 */
@Component
public class TextNormalizer {

    private static final Pattern INVISIBLE = Pattern.compile(
            "[\\u00AD\\u180E\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u2066-\\u2069\\uFEFF"
                    + "\\x00-\\x08\\x0B-\\x1F\\x7F-\\x9F]");
    private static final Pattern SPACE_RUNS = Pattern.compile("[ \\t]{2,}|\\t");
    private static final Pattern SPACED_LETTERS = Pattern.compile(
            "(?<![\\p{L}\\p{M}\\p{N}])[a-zA-Z](?: [a-zA-Z]){2,}(?![\\p{L}\\p{M}\\p{N}])");
    private static final Pattern SEPARATED_LETTERS = Pattern.compile(
            "(?<![\\p{L}\\p{M}\\p{N}@./_*-])[a-zA-Z](?:[.\\-_*][a-zA-Z]){2,}"
                    + "(?![\\p{L}\\p{M}\\p{N}@/]|[.\\-_*][\\p{L}\\p{N}])");
    private static final Pattern NOT_ASCII_LETTER = Pattern.compile("[^a-zA-Z]");
    private static final Pattern DEVANAGARI_DIGIT = Pattern.compile("[\\u0966-\\u096F]");

    public MappedText normalize(String input) {
        MappedText text = nfkc(MappedText.of(input));
        text = text.replaceAll(INVISIBLE, match -> "");
        text = text.replaceAll(SPACE_RUNS, match -> " ");
        text = text.replaceAll(SPACED_LETTERS, match -> NOT_ASCII_LETTER.matcher(match.group()).replaceAll(""));
        text = text.replaceAll(SEPARATED_LETTERS, match -> NOT_ASCII_LETTER.matcher(match.group()).replaceAll(""));
        text = text.replaceAll(DEVANAGARI_DIGIT, match -> String.valueOf((char) ('0' + match.group().charAt(0) - '\u0966')));
        return caseFold(text);
    }

    /** NFKC per base character plus its combining marks, so each output char maps to a small original range. */
    private static MappedText nfkc(MappedText text) {
        String value = text.value();
        if (Normalizer.isNormalized(value, Normalizer.Form.NFKC)) {
            return text;
        }
        List<Edit> edits = new ArrayList<>();
        int i = 0;
        while (i < value.length()) {
            int start = i;
            i += Character.charCount(value.codePointAt(i));
            while (i < value.length() && isCombiningMark(value.codePointAt(i))) {
                i += Character.charCount(value.codePointAt(i));
            }
            String cluster = value.substring(start, i);
            String normalized = Normalizer.normalize(cluster, Normalizer.Form.NFKC);
            if (!normalized.equals(cluster)) {
                edits.add(new Edit(start, i, normalized));
            }
        }
        return text.apply(edits);
    }

    private static boolean isCombiningMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    private static MappedText caseFold(MappedText text) {
        String value = text.value();
        List<Edit> edits = new ArrayList<>();
        int i = 0;
        while (i < value.length()) {
            int codePoint = value.codePointAt(i);
            int width = Character.charCount(codePoint);
            if (Character.isUpperCase(codePoint) || Character.isTitleCase(codePoint)) {
                edits.add(new Edit(i, i + width, new String(Character.toChars(codePoint)).toLowerCase(Locale.ROOT)));
            }
            i += width;
        }
        return text.apply(edits);
    }
}
