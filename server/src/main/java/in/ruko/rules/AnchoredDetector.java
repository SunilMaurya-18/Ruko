package in.ruko.rules;

import in.ruko.pipeline.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TERMS, REGEX, and ENTITY rules: an anchor span, optionally filtered by {@code match}/{@code exclude} on its text,
 * suppressed when a negation cue shares its sentence (outside this rule's own anchors), and required to co-occur
 * with every {@code with} condition in the same sentence or message. Evidence spans the anchor plus the nearest
 * same-sentence condition match.
 */
final class AnchoredDetector implements Detector {

    static final int MAX_EVIDENCE_CHARS = 160;
    static final int SOURCE_EVIDENCE_CHARS = 120;

    private final SpanMatcher anchors;
    private final Pattern match;
    private final Pattern exclude;
    private final SpanMatcher negation;
    private final List<Condition> with;
    private final Set<Source> sources;

    AnchoredDetector(SpanMatcher anchors, Pattern match, Pattern exclude, SpanMatcher negation,
                     List<Condition> with, Set<Source> sources) {
        this.anchors = anchors;
        this.match = match;
        this.exclude = exclude;
        this.negation = negation;
        this.with = List.copyOf(with);
        this.sources = Set.copyOf(sources);
    }

    @Override
    public List<Found> find(RuleText text) {
        List<Found> found = new ArrayList<>();
        if (sources.contains(text.source()) && !text.sentences().isEmpty()) {
            found.add(firstSentence(text));
        }
        List<Span> all = anchors.find(text);
        List<List<Span>> conditionSpans = with.stream().map(c -> c.matcher().find(text)).toList();
        for (Span anchor : all) {
            String value = anchor.value() != null ? anchor.value() : text.text().substring(anchor.start(), anchor.end());
            if (match != null && !match.matcher(value).find() || exclude != null && exclude.matcher(value).find()) {
                continue;
            }
            Span sentence = text.sentenceAt(anchor.start());
            if (negation != null && negated(text, sentence, all)) {
                continue;
            }
            Found hit = withConditions(text, anchor, sentence, conditionSpans);
            if (hit != null) {
                found.add(new Found(hit.start(), hit.end(), anchor.value()));
            }
        }
        return found;
    }

    private Found withConditions(RuleText text, Span anchor, Span sentence, List<List<Span>> conditionSpans) {
        int start = anchor.start();
        int end = anchor.end();
        for (int i = 0; i < with.size(); i++) {
            boolean sentenceScope = with.get(i).scope() == Scope.SENTENCE;
            int from = sentenceScope ? sentence.start() : 0;
            int to = sentenceScope ? sentence.end() : text.length();
            Span best = null;
            for (Span candidate : conditionSpans.get(i)) {
                if (!candidate.inside(from, to) || candidate.inside(anchor.start(), anchor.end())) {
                    continue;
                }
                if (best == null || candidate.distanceTo(anchor) < best.distanceTo(anchor)) {
                    best = candidate;
                }
            }
            if (best == null) {
                return null;
            }
            if (sentenceScope) {
                start = Math.min(start, best.start());
                end = Math.max(end, best.end());
            }
        }
        if (end - start > MAX_EVIDENCE_CHARS) {
            return new Found(anchor.start(), anchor.end(), null);
        }
        return new Found(start, end, null);
    }

    private boolean negated(RuleText text, Span sentence, List<Span> ownAnchors) {
        char[] chars = text.text().substring(sentence.start(), sentence.end()).toCharArray();
        for (Span anchor : ownAnchors) {
            for (int i = Math.max(anchor.start(), sentence.start()); i < Math.min(anchor.end(), sentence.end()); i++) {
                chars[i - sentence.start()] = ' ';
            }
        }
        return !negation.findIn(new String(chars)).isEmpty();
    }

    private static Found firstSentence(RuleText text) {
        Span first = text.sentences().getFirst();
        int end = first.end();
        if (end - first.start() > SOURCE_EVIDENCE_CHARS) {
            int cut = text.text().lastIndexOf(' ', first.start() + SOURCE_EVIDENCE_CHARS);
            end = cut > first.start() ? cut : first.start() + SOURCE_EVIDENCE_CHARS;
        }
        return new Found(first.start(), end, null);
    }
}
