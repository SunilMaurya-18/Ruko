package in.ruko.guardrail;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Text helpers shared by the lint rules. */
final class LintText {

    private static final Pattern QUOTED = Pattern.compile("“([^”]{1,400})”|\"([^\"]{1,400})\"|‘([^’]{1,400})’|«([^»]{1,400})»");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private LintText() {
    }

    /**
     * Removes quoted segments whose content appears verbatim in the input, so a card may quote the message without
     * the quote counting as Ruko's own words. A quote that is not in the input stays and is linted.
     */
    static String unquoted(String text, String input) {
        Matcher matcher = QUOTED.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String inner = firstGroup(matcher).strip();
            boolean verbatim = !inner.isEmpty() && input.contains(inner);
            matcher.appendReplacement(out, verbatim ? " " : Matcher.quoteReplacement(matcher.group()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Whole words or phrases, case-insensitive; spaces inside a term match any run of whitespace. */
    static Pattern words(List<String> terms) {
        String alternatives = terms.stream()
                .map(term -> SPACES.splitAsStream(term.strip()).map(Pattern::quote).collect(Collectors.joining("\\s+")))
                .collect(Collectors.joining("|"));
        return Pattern.compile("(?<![\\p{L}\\p{M}\\p{N}])(?:" + alternatives + ")(?![\\p{L}\\p{M}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    static int wordCount(String text) {
        String stripped = text == null ? "" : text.strip();
        return stripped.isEmpty() ? 0 : SPACES.split(stripped).length;
    }

    private static String firstGroup(Matcher matcher) {
        for (int i = 1; i <= matcher.groupCount(); i++) {
            if (matcher.group(i) != null) {
                return matcher.group(i);
            }
        }
        return "";
    }
}
