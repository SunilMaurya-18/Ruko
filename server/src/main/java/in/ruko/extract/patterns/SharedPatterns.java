package in.ruko.extract.patterns;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Loads the regex files in {@code shared/patterns}, which the PWA also bundles. Patterns are written in the
 * Java/JavaScript common subset; sources are NFKC-normalised like the text they run on. Any error aborts boot.
 */
@Component
public class SharedPatterns {

    public static final String PII_RESOURCE = "patterns/pii.v0.json";
    public static final String ENTITIES_RESOURCE = "patterns/entities.v0.json";

    public enum Kind { EXACT, CANONICAL, PHRASE }

    public enum CaseMode { UPPER, LOWER, NONE }

    public record PiiRule(String id, String placeholder, Pattern pattern, boolean keepPrefixGroup) {
    }

    public record EntityPattern(Pattern pattern, String value) {
    }

    public record EntityType(String id, Kind kind, CaseMode caseMode, String trim, List<EntityPattern> patterns) {
    }

    private record PiiFile(int version, List<PiiRuleJson> rules) {
    }

    private record PiiRuleJson(String id, String placeholder, String pattern, String flags, boolean keepPrefixGroup) {
    }

    private record EntitiesFile(int version, List<EntityTypeJson> types) {
    }

    private record EntityTypeJson(String id, String kind, @JsonProperty("case") String caseMode,
                                  String trim, List<EntityPatternJson> patterns) {
    }

    private record EntityPatternJson(String pattern, String flags, String value) {
    }

    private final List<PiiRule> piiRules;
    private final List<EntityType> entityTypes;

    public SharedPatterns() {
        this.piiRules = loadPii();
        this.entityTypes = loadEntities();
    }

    public List<PiiRule> piiRules() {
        return piiRules;
    }

    public List<EntityType> entityTypes() {
        return entityTypes;
    }

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static List<PiiRule> loadPii() {
        PiiFile file = read(PII_RESOURCE, PiiFile.class);
        Set<String> ids = new HashSet<>();
        return file.rules().stream().map(rule -> {
            require(ids.add(rule.id()), PII_RESOURCE, "duplicate id " + rule.id());
            require(rule.placeholder() != null && rule.placeholder().matches("\\[[A-Z]+]"),
                    PII_RESOURCE, "bad placeholder for " + rule.id());
            Pattern pattern = compile(PII_RESOURCE, rule.id(), rule.pattern(), rule.flags());
            require(!rule.keepPrefixGroup() || pattern.matcher("").groupCount() >= 1,
                    PII_RESOURCE, rule.id() + " keeps a prefix group but has none");
            return new PiiRule(rule.id(), rule.placeholder(), pattern, rule.keepPrefixGroup());
        }).toList();
    }

    private static List<EntityType> loadEntities() {
        EntitiesFile file = read(ENTITIES_RESOURCE, EntitiesFile.class);
        Set<String> ids = new HashSet<>();
        return file.types().stream().map(type -> {
            require(ids.add(type.id()), ENTITIES_RESOURCE, "duplicate id " + type.id());
            Kind kind = Kind.valueOf(type.kind().toUpperCase(Locale.ROOT));
            List<EntityPattern> patterns = type.patterns().stream().map(p -> {
                require((kind == Kind.CANONICAL) == (p.value() != null), ENTITIES_RESOURCE,
                        type.id() + ": canonical patterns need a value, others must not have one");
                return new EntityPattern(compile(ENTITIES_RESOURCE, type.id(), p.pattern(), p.flags()), p.value());
            }).toList();
            require(!patterns.isEmpty(), ENTITIES_RESOURCE, type.id() + " has no patterns");
            CaseMode caseMode = type.caseMode() == null
                    ? CaseMode.NONE : CaseMode.valueOf(type.caseMode().toUpperCase(Locale.ROOT));
            return new EntityType(type.id(), kind, caseMode, type.trim() == null ? "" : type.trim(), patterns);
        }).toList();
    }

    private static Pattern compile(String resource, String id, String source, String flags) {
        return PortableRegex.compile(resource, id, source, flags);
    }

    private static <T> T read(String resource, Class<T> type) {
        try (InputStream in = SharedPatterns.class.getClassLoader().getResourceAsStream(resource)) {
            require(in != null, resource, "missing from classpath");
            return MAPPER.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException(resource + " could not be read", e);
        }
    }

    private static void require(boolean condition, String resource, String problem) {
        if (!condition) {
            throw new IllegalStateException(resource + ": " + problem);
        }
    }
}
