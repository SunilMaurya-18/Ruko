package in.ruko.guardrail;

import in.ruko.api.dto.AnalyzeResponse;
import in.ruko.extract.Entities;
import in.ruko.pipeline.Engine;
import in.ruko.pipeline.Lang;
import in.ruko.rules.Band;
import in.ruko.rules.ContentClass;
import java.util.List;
import java.util.Map;

/**
 * The last resort when even the template draft fails the linter. The text is fixed here rather than in the
 * catalogue so that no catalogue edit can break it. Band and class still come from the rules; no reasons, quotes,
 * or analogy are shown.
 */
public final class FallbackTemplates {

    private static final Map<Lang, String> GENERIC = Map.of(
            Lang.HI, "रुको अभी इस मैसेज को ठीक से समझा नहीं पाया। पैसा भेजने से पहले SEBI Check पर देखें या किसी भरोसे वाले से पूछें।",
            Lang.EN, "Ruko could not explain this message right now. Before you send any money, look it up on SEBI Check or ask someone you trust.");

    private FallbackTemplates() {
    }

    public static String text(Lang lang) {
        return GENERIC.get(lang);
    }

    public static AnalyzeResponse generic(Lang lang, Entities entities, Band band, ContentClass contentClass,
                                          AnalyzeResponse.Counts counts) {
        return new AnalyzeResponse(lang, entities, List.of(), List.of(), List.of(), band, contentClass, counts,
                List.of(new AnalyzeResponse.Card(null, GENERIC.get(lang))), null, AnalyzeResponse.FOOTER_KEY,
                Engine.TEMPLATE);
    }
}
