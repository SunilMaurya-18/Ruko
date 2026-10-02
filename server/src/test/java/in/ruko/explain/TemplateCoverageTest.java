package in.ruko.explain;

import static org.assertj.core.api.Assertions.assertThat;

import in.ruko.content.I18nBundle;
import in.ruko.pipeline.Lang;
import in.ruko.rules.Band;
import in.ruko.rules.ContentClass;
import in.ruko.rules.SignalRule;
import in.ruko.support.Pipeline;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Every enabled signal can be explained in both languages, within the card and analogy word limits. */
class TemplateCoverageTest {

    private static final I18nBundle I18N = Pipeline.I18N;

    @Test
    void everyEnabledSignalHasReasonSpokenAndCardInBothLanguages() {
        for (SignalRule rule : Pipeline.RULES.ruleSet().rules()) {
            if (!rule.enabled()) {
                continue;
            }
            for (Lang lang : Lang.values()) {
                for (String key : new String[] {rule.reasonKey(), rule.spokenKey(), rule.cardKey()}) {
                    assertThat(I18N.has(lang, key)).as(lang.wire() + " " + key).isTrue();
                    assertThat(I18N.text(lang, key)).as(lang.wire() + " " + key).isNotBlank();
                }
                assertThat(words(I18N.text(lang, rule.cardKey()))).as(lang.wire() + " " + rule.cardKey())
                        .isLessThanOrEqualTo(25);
            }
        }
    }

    @Test
    void analogiesFitInFortyWords() {
        Set<String> keys = Pipeline.RULES.ruleSet().rules().stream()
                .map(SignalRule::analogyKey).filter(key -> key != null).collect(Collectors.toSet());
        keys.add("analogy.fno_leverage");
        for (String key : keys) {
            for (Lang lang : Lang.values()) {
                assertThat(words(I18N.text(lang, key))).as(lang.wire() + " " + key).isBetween(1, 40);
            }
        }
    }

    @Test
    void screenLabelsExistInBothLanguages() {
        for (Lang lang : Lang.values()) {
            for (Band band : Band.values()) {
                assertThat(I18N.has(lang, "band." + band.wire())).isTrue();
                assertThat(I18N.has(lang, "band." + band.wire() + ".hint")).isTrue();
            }
            for (ContentClass contentClass : ContentClass.values()) {
                assertThat(I18N.has(lang, "class." + contentClass.wire())).isTrue();
            }
            assertThat(I18N.has(lang, "footer.no_flags_not_safe")).isTrue();
        }
    }

    @Test
    void bothLanguagesHaveTheSameKeys() throws IOException {
        assertThat(load("hi").stringPropertyNames()).isEqualTo(load("en").stringPropertyNames());
    }

    private static Properties load(String lang) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = TemplateCoverageTest.class.getClassLoader()
                .getResourceAsStream("i18n/messages_" + lang + ".properties")) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private static int words(String text) {
        return text.strip().split("\\s+").length;
    }
}
