package in.ruko.guardrail;

import in.ruko.pipeline.Lang;
import in.ruko.rules.Band;
import in.ruko.rules.ContentClass;
import in.ruko.rules.Severity;
import java.util.Map;

/**
 * What the rules decided, and the text the draft may quote from. {@code input} is the request text with masked
 * personal data shown as placeholders; {@code hits} maps each fired signal id to its catalogue severity.
 */
public record LintContext(Lang lang, String input, Band band, ContentClass contentClass, String analogyKey,
                          Map<String, Severity> hits) {

    public LintContext {
        hits = Map.copyOf(hits);
    }
}
