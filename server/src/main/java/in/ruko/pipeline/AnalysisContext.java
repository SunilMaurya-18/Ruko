package in.ruko.pipeline;

import in.ruko.extract.Entities;

/**
 * Everything the rules see for one request. {@code masked} maps back to the request text, so evidence spans can
 * be quoted verbatim. Lives for one request only.
 */
public record AnalysisContext(
        MappedText normalized,
        MappedText masked,
        Entities entities,
        Lang lang,
        Source source,
        boolean unreadable) {

    public String text() {
        return masked.value();
    }

    public String input() {
        return masked.original();
    }

    @Override
    public String toString() {
        return "AnalysisContext[lang=" + lang + ", source=" + source + ", unreadable=" + unreadable + "]";
    }
}
