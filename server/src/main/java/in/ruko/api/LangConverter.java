package in.ruko.api;

import in.ruko.pipeline.Lang;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/** Query parameters use the wire form ({@code hi}, {@code en}); anything else is a 400. */
@Component
public class LangConverter implements Converter<String, Lang> {

    @Override
    public Lang convert(String source) {
        for (Lang lang : Lang.values()) {
            if (lang.wire().equals(source)) {
                return lang;
            }
        }
        throw new IllegalArgumentException("unsupported language");
    }
}
