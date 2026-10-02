package in.ruko.voice;

import in.ruko.api.dto.AnalyzeResponse;
import in.ruko.content.I18nBundle;
import in.ruko.pipeline.Lang;
import in.ruko.rules.Band;
import in.ruko.rules.ContentClass;
import in.ruko.rules.RuleLoader;
import in.ruko.rules.RuleSet;
import in.ruko.rules.SignalRule;
import in.ruko.snapshot.SebiSnapshotIndex;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The only text TTS can speak. A request names one key from a fixed allowlist plus the result counts; the words come
 * from the catalogue, never from the request. The PWA builds the same text from the same catalogue for the phone's
 * own voice ({@code pwa/src/voice/script.js}); {@code fixtures/voice-scripts.v0.json} keeps the two in step.
 *
 * <p>A band key is spoken as label, hint, and count sentences (none for "not enough to judge"). Every other key is
 * its catalogue text. Counts are part of the cache key only where they change the words.
 */
@Component
public class VoiceScripts {

    public static final String ACTION = "voice.action";
    public static final String GENERIC = "voice.generic";
    public static final String FOOTER = "footer." + AnalyzeResponse.FOOTER_KEY;

    private static final String BAND = "band.";
    private static final ScriptCounts SAMPLE = new ScriptCounts(2, 1, 1);

    private final I18nBundle i18n;
    private final Map<String, String> params;
    private final Set<String> keys;

    public record Script(TtsCache.Key cacheKey, String text) {
    }

    public VoiceScripts(RuleLoader loader, I18nBundle i18n, SebiSnapshotIndex snapshot) {
        this.i18n = i18n;
        this.params = Map.of("date", snapshot.date().map(Object::toString).orElse(""));

        RuleSet rules = loader.ruleSet();
        Set<String> allowed = new LinkedHashSet<>();
        for (Band band : Band.values()) {
            allowed.add(BAND + band.wire());
        }
        for (ContentClass contentClass : ContentClass.values()) {
            allowed.add("class." + contentClass.wire());
        }
        for (SignalRule rule : rules.rules()) {
            if (rule.enabled() && (rule.detector() == null || snapshot.active())) {
                allowed.add(rule.spokenKey());
            }
        }
        allowed.addAll(rules.analogyKeys());
        allowed.add(ACTION);
        allowed.add(GENERIC);
        allowed.add(FOOTER);
        this.keys = Collections.unmodifiableSet(allowed);

        for (String key : keys) {
            for (Lang lang : Lang.values()) {
                build(key, lang, SAMPLE);
            }
        }
    }

    public Set<String> keys() {
        return keys;
    }

    public Script build(String key, Lang lang, ScriptCounts counts) {
        if (key == null || !keys.contains(key)) {
            throw new UnknownScriptException();
        }
        if (!key.startsWith(BAND)) {
            return new Script(new TtsCache.Key(key, lang, null), i18n.text(lang, key, params));
        }
        String text = i18n.text(lang, key) + (lang == Lang.HI ? "।" : ".") + " " + i18n.text(lang, key + ".hint");
        if (key.equals(BAND + Band.NOT_ENOUGH_TO_JUDGE.wire())) {
            return new Script(new TtsCache.Key(key, lang, null), text);
        }
        StringBuilder spoken = new StringBuilder(text).append(' ').append(count(lang, "red_flags", counts.redFlags()));
        if (counts.couldntVerify() > 0) {
            spoken.append(' ').append(count(lang, "couldnt_verify", counts.couldntVerify()));
        }
        if (counts.reassuring() > 0) {
            spoken.append(' ').append(count(lang, "reassuring", counts.reassuring()));
        }
        return new Script(new TtsCache.Key(key, lang, counts), spoken.toString());
    }

    private String count(Lang lang, String name, int n) {
        String form = n == 0 ? "zero" : n == 1 ? "one" : "other";
        return i18n.text(lang, "voice.count." + name + "." + form, Map.of("n", Integer.toString(n)));
    }
}
