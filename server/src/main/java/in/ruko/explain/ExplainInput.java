package in.ruko.explain;

import in.ruko.extract.Entities;
import in.ruko.pipeline.Lang;
import in.ruko.rules.Band;
import in.ruko.rules.ContentClass;
import in.ruko.rules.SignalHit;
import java.util.List;

/** The rule result an explainer turns into a response. {@code llmTagged}: some hits came from merged model tags. */
public record ExplainInput(Lang lang, Entities entities, List<SignalHit> hits, Band band, ContentClass contentClass,
                           String analogyKey, boolean llmTagged) {

    public ExplainInput {
        hits = List.copyOf(hits);
    }
}
