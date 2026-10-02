package in.ruko.pipeline;

import in.ruko.extract.EntityMatch;
import in.ruko.extract.Entities;
import java.util.List;

/**
 * Everything the rules see for one request. {@code masked} maps back to the request text, so evidence spans can
 * be quoted verbatim; {@code entityMatches} are spans in {@code masked}. Lives for one request only.
 */
public record AnalysisContext(
        MappedText normalized,
        MappedText masked,
        Entities entities,
        List<EntityMatch> entityMatches,
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
