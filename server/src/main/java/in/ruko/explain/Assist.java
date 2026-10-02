package in.ruko.explain;

import in.ruko.rules.LlmTag;
import java.util.List;
import java.util.Map;

/** What the optional LLM may contribute: tags with verbatim spans, and card text keyed by signal id. */
public record Assist(List<LlmTag> tags, Map<String, String> cards) {

    public static final Assist EMPTY = new Assist(List.of(), Map.of());

    public Assist {
        tags = List.copyOf(tags);
        cards = Map.copyOf(cards);
    }

    public boolean isEmpty() {
        return tags.isEmpty() && cards.isEmpty();
    }
}
