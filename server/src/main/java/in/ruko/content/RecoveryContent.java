package in.ruko.content;

import static in.ruko.content.OfficialLinks.read;
import static in.ruko.content.OfficialLinks.require;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.ruko.pipeline.Lang;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Track B steps from {@code content/recovery.v0.json}: money already sent (1930, the bank, cybercrime.gov.in) and a
 * problem with a registered broker or DP (SCORES). Text is catalogue text; links come from {@link OfficialLinks}.
 */
@Component
public class RecoveryContent {

    public static final String RESOURCE = "content/recovery.v0.json";

    public record Step(String text, @JsonInclude(JsonInclude.Include.NON_NULL) OfficialLinks.Link link) {
    }

    public record Page(Lang language, List<Step> cyberFraud, List<Step> scores) {
    }

    private record RecoveryFile(int version, String note, Paths paths) {
    }

    private record Paths(List<StepJson> cyberFraud, List<StepJson> scores) {
    }

    private record StepJson(String textKey, String link) {
    }

    private final I18nBundle i18n;
    private final OfficialLinks links;
    private final Paths paths;

    public RecoveryContent(I18nBundle i18n, OfficialLinks links) {
        this.i18n = i18n;
        this.links = links;
        RecoveryFile file = read(RESOURCE, RecoveryFile.class);
        require(file.paths() != null, RESOURCE, "has no paths");
        this.paths = file.paths();
        validate("cyber_fraud", paths.cyberFraud());
        validate("scores", paths.scores());
    }

    public Page page(Lang lang) {
        return new Page(lang, steps(paths.cyberFraud(), lang), steps(paths.scores(), lang));
    }

    private List<Step> steps(List<StepJson> steps, Lang lang) {
        return steps.stream()
                .map(step -> new Step(i18n.text(lang, step.textKey()),
                        step.link() == null ? null : links.link(step.link(), lang)))
                .toList();
    }

    private void validate(String path, List<StepJson> steps) {
        require(steps != null && !steps.isEmpty(), RESOURCE, path + " has no steps");
        for (StepJson step : steps) {
            for (Lang lang : Lang.values()) {
                require(step.textKey() != null && i18n.has(lang, step.textKey()), RESOURCE,
                        path + " needs catalogue text " + step.textKey() + " in " + lang.wire());
            }
            require(step.link() == null || links.has(step.link()), RESOURCE, path + " refers to unknown link " + step.link());
        }
    }
}
