package in.ruko.content;

import in.ruko.pipeline.Lang;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import org.springframework.stereotype.Component;

/** Catalogue text from {@code shared/i18n/messages_{hi,en}.properties} (UTF-8). A missing file aborts boot. */
@Component
public class I18nBundle {

    private final Map<Lang, Properties> bundles = new EnumMap<>(Lang.class);

    public I18nBundle() {
        for (Lang lang : Lang.values()) {
            bundles.put(lang, load("i18n/messages_" + lang.wire() + ".properties"));
        }
    }

    public boolean has(Lang lang, String key) {
        return bundles.get(lang).containsKey(key);
    }

    public String text(Lang lang, String key) {
        String text = bundles.get(lang).getProperty(key);
        if (text == null) {
            throw new IllegalArgumentException("missing catalogue key " + key + " for " + lang.wire());
        }
        return text;
    }

    /** Replaces {@code {name}} placeholders; values come from the catalogue or the server, never from the request. */
    public String text(Lang lang, String key, Map<String, String> params) {
        String text = text(lang, key);
        for (Map.Entry<String, String> param : params.entrySet()) {
            text = text.replace("{" + param.getKey() + "}", param.getValue());
        }
        return text;
    }

    private static Properties load(String resource) {
        try (InputStream in = I18nBundle.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + ": missing from classpath");
            }
            Properties properties = new Properties();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException(resource + " could not be read", e);
        }
    }
}
