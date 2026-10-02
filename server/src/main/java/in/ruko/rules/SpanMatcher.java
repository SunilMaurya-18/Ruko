package in.ruko.rules;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Finds spans by terms, patterns, and entity types. Terms match whole words of the case-folded text; a term ending
 * in {@code *} matches any word that starts with it. Term sources get the same NFKC and case-fold as the text.
 * Term hits inside a UPI id or URL do not count ("vip" in {@code vip.club@ybl}); pattern hits may.
 */
final class SpanMatcher {

    private static final String NOT_AFTER_WORD = "(?<![\\p{L}\\p{M}\\p{N}])";
    private static final String NOT_BEFORE_WORD = "(?![\\p{L}\\p{M}\\p{N}])";
    private static final List<String> OPAQUE_ENTITIES = List.of("upi_ids", "urls");

    private final List<Pattern> termPatterns;
    private final List<Pattern> patterns;
    private final List<String> entities;

    SpanMatcher(List<String> terms, List<Pattern> patterns, List<String> entities) {
        this.termPatterns = terms.isEmpty() ? List.of() : List.of(compileTerms(terms));
        this.patterns = List.copyOf(patterns);
        this.entities = List.copyOf(entities);
    }

    private SpanMatcher(Compiled compiled) {
        this.termPatterns = List.copyOf(compiled.termPatterns());
        this.patterns = List.copyOf(compiled.patterns());
        this.entities = List.copyOf(compiled.entities());
    }

    private record Compiled(List<Pattern> termPatterns, List<Pattern> patterns, List<String> entities) {
    }

    static SpanMatcher union(List<SpanMatcher> matchers) {
        List<Pattern> termPatterns = new ArrayList<>();
        List<Pattern> patterns = new ArrayList<>();
        List<String> entities = new ArrayList<>();
        for (SpanMatcher matcher : matchers) {
            termPatterns.addAll(matcher.termPatterns);
            patterns.addAll(matcher.patterns);
            entities.addAll(matcher.entities);
        }
        return new SpanMatcher(new Compiled(termPatterns, patterns, entities.stream().distinct().toList()));
    }

    static String foldTerm(String term) {
        return Normalizer.normalize(term, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    List<Span> find(RuleText text) {
        List<Span> spans = new ArrayList<>();
        List<Span> opaque = OPAQUE_ENTITIES.stream().flatMap(type -> text.entities(type).stream()).toList();
        for (Pattern terms : termPatterns) {
            for (Span span : collect(terms, text.text())) {
                if (opaque.stream().noneMatch(entity -> span.inside(entity.start(), entity.end()))) {
                    spans.add(span);
                }
            }
        }
        for (Pattern pattern : patterns) {
            spans.addAll(collect(pattern, text.text()));
        }
        for (String entity : entities) {
            spans.addAll(text.entities(entity));
        }
        spans.sort(Comparator.comparingInt(Span::start).thenComparing(Comparator.comparingInt(Span::end).reversed()));
        return spans;
    }

    /** Terms and patterns over a plain string; entity spans need a {@link RuleText}. */
    List<Span> findIn(String text) {
        List<Span> spans = new ArrayList<>();
        termPatterns.forEach(terms -> spans.addAll(collect(terms, text)));
        patterns.forEach(pattern -> spans.addAll(collect(pattern, text)));
        return spans;
    }

    private static List<Span> collect(Pattern pattern, String text) {
        List<Span> spans = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (matcher.end() > matcher.start()) {
                spans.add(new Span(matcher.start(), matcher.end(), null));
            }
        }
        return spans;
    }

    private static Pattern compileTerms(List<String> terms) {
        String alternatives = terms.stream()
                .map(SpanMatcher::foldTerm)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(term -> term.endsWith("*")
                        ? Pattern.quote(term.substring(0, term.length() - 1))
                        : Pattern.quote(term) + NOT_BEFORE_WORD)
                .collect(Collectors.joining("|"));
        return Pattern.compile(NOT_AFTER_WORD + "(?:" + alternatives + ")");
    }
}
