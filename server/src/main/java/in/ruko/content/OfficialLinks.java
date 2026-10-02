package in.ruko.content;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import in.ruko.guardrail.OutboundLinkPolicy;
import in.ruko.pipeline.Lang;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Every place Ruko may send a user, from {@code content/links.v0.json} (the PWA bundles the same file). Boot fails if a
 * url does not pass {@link OutboundLinkPolicy}, an id repeats, or a label is missing from the catalogue.
 */
@Component
public class OfficialLinks {

    public static final String RESOURCE = "content/links.v0.json";

    static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public record Link(String id, String url, String label) {
    }

    public record Page(Lang language, List<Link> links) {
    }

    private record LinksFile(int version, String note, List<LinkJson> links) {
    }

    private record LinkJson(String id, String url, String labelKey) {
    }

    private final I18nBundle i18n;
    private final Map<String, LinkJson> byId = new LinkedHashMap<>();

    public OfficialLinks(OutboundLinkPolicy policy, I18nBundle i18n) {
        this.i18n = i18n;
        LinksFile file = read(RESOURCE, LinksFile.class);
        require(file.links() != null && !file.links().isEmpty(), RESOURCE, "has no links");
        for (LinkJson link : file.links()) {
            require(link.id() != null && byId.put(link.id(), link) == null, RESOURCE, "repeats id " + link.id());
            require(policy.allows(link.url()), RESOURCE, link.id() + " is not an allowed link");
            for (Lang lang : Lang.values()) {
                require(link.labelKey() != null && i18n.has(lang, link.labelKey()), RESOURCE,
                        link.id() + " needs catalogue text " + link.labelKey() + " in " + lang.wire());
            }
        }
    }

    public Page page(Lang lang) {
        List<Link> links = new ArrayList<>();
        byId.keySet().forEach(id -> links.add(link(id, lang)));
        return new Page(lang, links);
    }

    public boolean has(String id) {
        return byId.containsKey(id);
    }

    public Link link(String id, Lang lang) {
        LinkJson link = byId.get(id);
        if (link == null) {
            throw new IllegalArgumentException("unknown link " + id);
        }
        return new Link(link.id(), link.url(), i18n.text(lang, link.labelKey()));
    }

    static <T> T read(String resource, Class<T> type) {
        try (InputStream in = OfficialLinks.class.getClassLoader().getResourceAsStream(resource)) {
            require(in != null, resource, "missing from classpath");
            return MAPPER.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException(resource + " could not be read", e);
        }
    }

    static void require(boolean condition, String resource, String problem) {
        if (!condition) {
            throw new IllegalStateException(resource + ": " + problem);
        }
    }
}
