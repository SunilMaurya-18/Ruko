package in.ruko.content;

import static org.assertj.core.api.Assertions.assertThat;

import in.ruko.guardrail.OutboundLinkPolicy;
import in.ruko.pipeline.Lang;
import in.ruko.support.Pipeline;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Plan Phase 5: every link Ruko can show passes {@link OutboundLinkPolicy}, and the recovery text is linter-clean. */
class RecoveryLinksTest {

    private static final OfficialLinks LINKS = new OfficialLinks(Pipeline.LINKS, Pipeline.I18N);
    private static final RecoveryContent RECOVERY = new RecoveryContent(Pipeline.I18N, LINKS);

    @Test
    void everyOfficialLinkPassesThePolicy() {
        for (Lang lang : Lang.values()) {
            List<OfficialLinks.Link> links = LINKS.page(lang).links();
            assertThat(links).extracting(OfficialLinks.Link::url).containsExactly(
                    OutboundLinkPolicy.TEL_1930, "https://cybercrime.gov.in", "https://scores.sebi.gov.in",
                    "https://siportal.sebi.gov.in/intermediary/sebi-check");
            links.forEach(link -> assertThat(Pipeline.LINKS.allows(link.url())).as(link.id()).isTrue());
            links.forEach(link -> assertThat(link.label()).as(link.id()).isNotBlank());
        }
    }

    @Test
    void everyRecoveryStepLinkPassesThePolicy() {
        for (Lang lang : Lang.values()) {
            RecoveryContent.Page page = RECOVERY.page(lang);
            Stream.concat(page.cyberFraud().stream(), page.scores().stream())
                    .filter(step -> step.link() != null)
                    .forEach(step -> assertThat(Pipeline.LINKS.allows(step.link().url())).as(step.link().id()).isTrue());
        }
    }

    @Test
    void moneySentStartsWith1930ThenTheBankThenCybercrime() {
        for (Lang lang : Lang.values()) {
            List<RecoveryContent.Step> steps = RECOVERY.page(lang).cyberFraud();
            assertThat(steps.getFirst().link().url()).isEqualTo(OutboundLinkPolicy.TEL_1930);
            assertThat(steps.get(1).link()).isNull();
            assertThat(steps.get(2).link().url()).isEqualTo("https://cybercrime.gov.in");
        }
    }

    @Test
    void brokerProblemsPointToScores() {
        for (Lang lang : Lang.values()) {
            assertThat(RECOVERY.page(lang).scores().stream().filter(step -> step.link() != null)
                    .map(step -> step.link().url())).containsExactly("https://scores.sebi.gov.in");
        }
    }

    @Test
    void recoveryTextPassesL1L3AndL5() {
        for (Lang lang : Lang.values()) {
            RecoveryContent.Page page = RECOVERY.page(lang);
            Stream.concat(page.cyberFraud().stream(), page.scores().stream()).forEach(step -> {
                assertThat(Pipeline.LINTER.lintPlainText(lang, "step", step.text()).violations()).as(step.text()).isEmpty();
                if (step.link() != null) {
                    assertThat(Pipeline.LINTER.lintPlainText(lang, "label", step.link().label()).violations()).isEmpty();
                }
            });
        }
    }

    @Test
    void thePolicyRefusesLookalikes() {
        assertThat(Pipeline.LINKS.allows("http://cybercrime.gov.in")).isFalse();
        assertThat(Pipeline.LINKS.allows("https://cybercrime.gov.in.example")).isFalse();
        assertThat(Pipeline.LINKS.allows("https://user@scores.sebi.gov.in")).isFalse();
        assertThat(Pipeline.LINKS.allows("tel:1930,123")).isFalse();
    }
}
