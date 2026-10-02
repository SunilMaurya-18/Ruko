package in.ruko.extract.patterns;

import java.text.Normalizer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Compiles shared patterns that the PWA also runs with JavaScript {@code RegExp(source, "gu" + flags)}. Rejects
 * syntax whose meaning differs between the two engines (or that JavaScript's {@code u} mode refuses): {@code .},
 * {@code \s}/{@code \b}/{@code \w} and friends, possessive and atomic groups, inline flags, nested or intersected
 * classes, and identity escapes of ordinary characters. The only flag is {@code i}.
 */
public final class PortableRegex {

    private static final String SYNTAX_CHARS = "^$\\.*+?()[]{}|/-";
    private static final String LETTER_ESCAPES = "dDntrfpPux";

    private PortableRegex() {
    }

    public static Pattern compile(String resource, String id, String source, String flags) {
        if (source == null || source.isBlank()) {
            throw new IllegalStateException(resource + ": " + id + " has an empty pattern");
        }
        int javaFlags = 0;
        for (char flag : (flags == null ? "" : flags).toCharArray()) {
            if (flag != 'i') {
                throw new IllegalStateException(resource + ": " + id + " uses unsupported flag " + flag);
            }
            javaFlags |= Pattern.CASE_INSENSITIVE;
        }
        String problem = subsetProblem(source);
        if (problem != null) {
            throw new IllegalStateException(resource + ": " + id + " is outside the Java/JavaScript regex subset: " + problem);
        }
        try {
            return Pattern.compile(Normalizer.normalize(source, Normalizer.Form.NFKC), javaFlags);
        } catch (PatternSyntaxException e) {
            throw new IllegalStateException(resource + ": invalid pattern for " + id, e);
        }
    }

    /** Returns why {@code source} is not portable, or {@code null} if it is. */
    static String subsetProblem(String source) {
        boolean inClass = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\\') {
                if (i + 1 >= source.length()) {
                    return "trailing backslash";
                }
                char escaped = source.charAt(++i);
                if (LETTER_ESCAPES.indexOf(escaped) < 0 && SYNTAX_CHARS.indexOf(escaped) < 0) {
                    return "escape \\" + escaped;
                }
                if ((escaped == 'p' || escaped == 'P') && !source.startsWith("{", i + 1)) {
                    return "\\p without braces";
                }
                continue;
            }
            if (inClass) {
                if (c == '[') {
                    return "nested character class";
                }
                if (c == '&' && i + 1 < source.length() && source.charAt(i + 1) == '&') {
                    return "class intersection";
                }
                if (c == ']') {
                    inClass = false;
                }
                continue;
            }
            switch (c) {
                case '[' -> {
                    inClass = true;
                    if (source.startsWith("^", i + 1)) {
                        i++;
                    }
                    if (source.startsWith("]", i + 1)) {
                        return "empty or ']'-first character class";
                    }
                }
                case '.' -> {
                    return "unescaped '.'";
                }
                case '(' -> {
                    if (source.startsWith("?", i + 1)) {
                        String rest = source.substring(i + 2);
                        if (!(rest.startsWith(":") || rest.startsWith("=") || rest.startsWith("!")
                                || rest.startsWith("<=") || rest.startsWith("<!"))) {
                            return "group syntax (?" + (rest.isEmpty() ? "" : rest.charAt(0));
                        }
                    }
                }
                case '*', '+', '?', '}' -> {
                    if (i + 1 < source.length() && source.charAt(i + 1) == '+') {
                        return "possessive quantifier";
                    }
                }
                default -> {
                }
            }
        }
        return inClass ? "unclosed character class" : null;
    }
}
