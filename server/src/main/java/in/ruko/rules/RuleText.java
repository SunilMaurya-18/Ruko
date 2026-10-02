package in.ruko.rules;

import in.ruko.extract.EntityMatch;
import in.ruko.pipeline.AnalysisContext;
import in.ruko.pipeline.MappedText;
import in.ruko.pipeline.Source;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The masked text as the rules see it: sentences, entity spans (plus {@code accounts} for {@code [ACCT]} and
 * {@code phones} for {@code [PHONE]}), and a
 * way to quote any span from the original request. Placeholders are quoted as placeholders, so evidence never
 * re-exposes masked personal data.
 */
public final class RuleText {

    static final String ACCOUNTS = "accounts";
    static final String PHONES = "phones";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\[(?:PHONE|ACCT|AADHAAR|PAN|OTP)]");
    private static final Set<String> ABBREVIATIONS = Set.of("rs", "inr", "no", "mr", "mrs", "dr", "st", "vs");

    private final MappedText masked;
    private final String text;
    private final Source source;
    private final List<Span> sentences;
    private final List<Span> placeholders;
    private final Map<String, List<Span>> entities = new HashMap<>();

    private RuleText(MappedText masked, List<EntityMatch> matches, Source source) {
        this.masked = masked;
        this.text = masked.value();
        this.source = source;
        this.sentences = split(text);
        this.placeholders = new ArrayList<>();
        Matcher placeholder = PLACEHOLDER.matcher(text);
        while (placeholder.find()) {
            placeholders.add(new Span(placeholder.start(), placeholder.end(), placeholder.group()));
            String type = switch (placeholder.group()) {
                case "[ACCT]" -> ACCOUNTS;
                case "[PHONE]" -> PHONES;
                default -> null;
            };
            if (type != null) {
                entities.computeIfAbsent(type, k -> new ArrayList<>())
                        .add(new Span(placeholder.start(), placeholder.end(), placeholder.group()));
            }
        }
        for (EntityMatch match : matches) {
            entities.computeIfAbsent(match.type(), k -> new ArrayList<>())
                    .add(new Span(match.start(), match.end(), match.value()));
        }
    }

    public static RuleText of(AnalysisContext ctx) {
        return new RuleText(ctx.masked(), ctx.entityMatches(), ctx.source());
    }

    public static RuleText of(MappedText masked, List<EntityMatch> matches, Source source) {
        return new RuleText(masked, matches, source);
    }

    public String text() {
        return text;
    }

    public int length() {
        return text.length();
    }

    public Source source() {
        return source;
    }

    List<Span> entities(String type) {
        return entities.getOrDefault(type, List.of());
    }

    List<Span> sentences() {
        return sentences;
    }

    /** The sentence containing {@code position}, or the whole text if it falls between sentences. */
    Span sentenceAt(int position) {
        for (Span sentence : sentences) {
            if (position >= sentence.start() && position < sentence.end()) {
                return sentence;
            }
        }
        return new Span(0, text.length(), null);
    }

    /** Verbatim slice of the request for {@code [start, end)} of the masked text, placeholders kept masked. */
    public String quote(int start, int end) {
        StringBuilder out = new StringBuilder();
        int cursor = start;
        for (Span placeholder : placeholders) {
            if (placeholder.end() <= start || placeholder.start() >= end) {
                continue;
            }
            if (placeholder.start() > cursor) {
                out.append(masked.originalSlice(cursor, placeholder.start()));
            }
            out.append(placeholder.value());
            cursor = placeholder.end();
        }
        if (cursor < end) {
            out.append(masked.originalSlice(cursor, end));
        }
        return out.toString().strip();
    }

    private static List<Span> split(String text) {
        List<Span> sentences = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isBreak(text, i)) {
                addTrimmed(sentences, text, start, i + 1);
                start = i + 1;
            }
        }
        addTrimmed(sentences, text, start, text.length());
        return List.copyOf(sentences);
    }

    private static boolean isBreak(String text, int i) {
        char c = text.charAt(i);
        if (c == '!' || c == '?' || c == '\n' || c == '\u0964' || c == '\u0965') {
            return true;
        }
        if (c != '.') {
            return false;
        }
        boolean atEnd = i + 1 == text.length() || Character.isWhitespace(text.charAt(i + 1));
        if (!atEnd) {
            return false;
        }
        int wordStart = i;
        while (wordStart > 0 && Character.isLetter(text.charAt(wordStart - 1))) {
            wordStart--;
        }
        return !ABBREVIATIONS.contains(text.substring(wordStart, i));
    }

    private static void addTrimmed(List<Span> sentences, String text, int start, int end) {
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (end > start) {
            sentences.add(new Span(start, end, null));
        }
    }
}
