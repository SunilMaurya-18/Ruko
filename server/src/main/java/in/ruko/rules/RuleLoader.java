package in.ruko.rules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import in.ruko.content.I18nBundle;
import in.ruko.extract.patterns.PortableRegex;
import in.ruko.pipeline.Lang;
import in.ruko.pipeline.Source;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Loads {@code rules/signals.v0.json}, validates it against {@code schemas/signals.v0.json}, and compiles it.
 * Any problem (schema, unknown key, bad regex, duplicate id, missing catalogue text, unknown set or marker, an
 * enabled rule whose detector this build lacks) throws, which aborts boot.
 */
@Component
public class RuleLoader {

    public static final String RESOURCE = "rules/signals.v0.json";
    public static final String SCHEMA = "schemas/signals.v0.json";

    static final String NEGATION_SET = "negation";
    static final Set<String> AVAILABLE_DETECTORS = Set.of("snapshot");

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private record RulesFile(int version, String note, Map<String, SetJson> sets, Map<String, MarkerJson> markers,
                             List<TriggerJson> analogyTriggers, List<RuleJson> signals) {
    }

    private record SetJson(List<String> terms, List<String> patterns) {
    }

    private record ConditionJson(List<String> terms, List<String> patterns, List<String> entities, List<String> sets,
                                 String scope) {
    }

    private record MarkerJson(String note, List<String> terms, List<String> patterns, List<String> entities,
                              List<String> sets, List<ConditionJson> with) {
    }

    private record TriggerJson(String analogyKey, List<String> terms, List<String> patterns) {
    }

    private record UnlessJson(List<String> markers, List<String> severities) {
    }

    private record PrefixJson(String prefix, String role, List<String> terms) {
    }

    private record PrefixSourceJson(String name, String url, String checked, String note) {
    }

    private record PrefixMapJson(int digits, List<PrefixJson> prefixes, PrefixSourceJson source) {
    }

    private record RuleJson(String id, String severity, String type, boolean enabled, Boolean llmTag,
                            String reasonKey, String spokenKey, String cardKey, String analogyKey, String note,
                            List<String> terms, List<String> patterns, List<String> entities, List<String> sets,
                            String match, String exclude, Boolean negatable, List<ConditionJson> with,
                            List<String> sources, UnlessJson unless, PrefixMapJson prefixMap, String detector,
                            String action) {
    }

    private final RuleSet ruleSet;

    public RuleLoader(I18nBundle i18n) {
        this.ruleSet = load(readResource(RESOURCE), i18n);
    }

    public RuleSet ruleSet() {
        return ruleSet;
    }

    public static RuleSet load(String json, I18nBundle i18n) {
        JsonNode tree;
        try {
            tree = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(RESOURCE + ": not valid JSON", e);
        }
        validateSchema(tree);
        RulesFile file;
        try {
            file = MAPPER.treeToValue(tree, RulesFile.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(RESOURCE + ": does not match the rule model", e);
        }
        return new Compiler(file, i18n).compile();
    }

    private static void validateSchema(JsonNode tree) {
        JsonNode schemaNode;
        try {
            schemaNode = MAPPER.readTree(readResource(SCHEMA));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(SCHEMA + ": not valid JSON", e);
        }
        JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
        Set<ValidationMessage> errors = schema.validate(tree);
        if (!errors.isEmpty()) {
            String detail = errors.stream().map(ValidationMessage::getMessage).sorted().limit(10)
                    .collect(Collectors.joining("; "));
            throw new IllegalStateException(RESOURCE + ": fails " + SCHEMA + ": " + detail);
        }
    }

    private static String readResource(String resource) {
        try (InputStream in = RuleLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + ": missing from classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(resource + " could not be read", e);
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static void require(boolean condition, String problem) {
        if (!condition) {
            throw new IllegalStateException(RESOURCE + ": " + problem);
        }
    }

    private static final class Compiler {

        private final RulesFile file;
        private final I18nBundle i18n;
        private final Map<String, SpanMatcher> sets = new HashMap<>();

        Compiler(RulesFile file, I18nBundle i18n) {
            this.file = file;
            this.i18n = i18n;
        }

        RuleSet compile() {
            file.sets().forEach((name, set) -> sets.put(name,
                    new SpanMatcher(orEmpty(set.terms()), patterns("set " + name, set.patterns()), List.of())));

            Map<String, Detector> markers = new LinkedHashMap<>();
            file.markers().forEach((name, marker) -> markers.put(name, new AnchoredDetector(
                    matcher("marker " + name, marker.terms(), marker.patterns(), marker.entities(), marker.sets()),
                    null, null, null, conditions("marker " + name, marker.with()), Set.of())));

            List<RuleSet.AnalogyTrigger> triggers = new ArrayList<>();
            for (TriggerJson trigger : file.analogyTriggers()) {
                requireText(trigger.analogyKey(), "analogy trigger");
                triggers.add(new RuleSet.AnalogyTrigger(trigger.analogyKey(),
                        matcher(trigger.analogyKey(), trigger.terms(), trigger.patterns(), null, null)));
            }

            Set<String> ids = new HashSet<>();
            List<SignalRule> rules = new ArrayList<>();
            for (RuleJson rule : file.signals()) {
                require(ids.add(rule.id()), "duplicate id " + rule.id());
                rules.add(rule(rule, rules.size(), markers.keySet()));
            }
            return new RuleSet(rules, markers, triggers);
        }

        private SignalRule rule(RuleJson json, int order, Set<String> markerNames) {
            String id = json.id();
            requireText(json.reasonKey(), id);
            requireText(json.spokenKey(), id);
            requireText(json.cardKey(), id);
            if (json.analogyKey() != null) {
                requireText(json.analogyKey(), id);
            }
            RuleType type = RuleType.valueOf(json.type());
            Severity severity = Severity.valueOf(json.severity());
            if (json.detector() != null) {
                require(!json.enabled() || AVAILABLE_DETECTORS.contains(json.detector()),
                        id + " is enabled but this build has no " + json.detector() + " detector");
            }

            Set<String> unlessMarkers = new HashSet<>();
            Set<Severity> unlessSeverities = EnumSet.noneOf(Severity.class);
            if (json.unless() != null) {
                for (String marker : orEmpty(json.unless().markers())) {
                    require(markerNames.contains(marker), id + " refers to unknown marker " + marker);
                    unlessMarkers.add(marker);
                }
                orEmpty(json.unless().severities()).forEach(s -> unlessSeverities.add(Severity.valueOf(s)));
            }

            Detector detector = switch (type) {
                case LLM_TAG -> Detector.NONE;
                case PREFIX_MAP -> prefixMap(id, json.prefixMap());
                case TERMS, REGEX, ENTITY -> json.detector() != null ? Detector.NONE : new AnchoredDetector(
                        matcher(id, json.terms(), json.patterns(), json.entities(), json.sets()),
                        json.match() == null ? null : PortableRegex.compile(RESOURCE, id + ".match", json.match(), null),
                        json.exclude() == null ? null : PortableRegex.compile(RESOURCE, id + ".exclude", json.exclude(), null),
                        Boolean.TRUE.equals(json.negatable()) ? set(id, NEGATION_SET) : null,
                        conditions(id, json.with()),
                        orEmpty(json.sources()).stream()
                                .map(s -> Source.valueOf(s.toUpperCase(Locale.ROOT)))
                                .collect(Collectors.toSet()));
            };
            return new SignalRule(id, severity, type, json.enabled(), Boolean.TRUE.equals(json.llmTag()),
                    json.reasonKey(), json.spokenKey(), json.cardKey(), json.analogyKey(), json.action(), json.detector(),
                    Set.copyOf(unlessMarkers), unlessSeverities, order, detector);
        }

        private Detector prefixMap(String id, PrefixMapJson json) {
            Map<String, SpanMatcher> roles = new LinkedHashMap<>();
            for (PrefixJson prefix : json.prefixes()) {
                require(roles.put(prefix.prefix(), new SpanMatcher(prefix.terms(), List.of(), List.of())) == null,
                        id + " repeats prefix " + prefix.prefix());
            }
            return new PrefixMapDetector(json.digits(), roles);
        }

        private List<Detector.Condition> conditions(String owner, List<ConditionJson> with) {
            List<Detector.Condition> conditions = new ArrayList<>();
            for (ConditionJson condition : orEmpty(with)) {
                conditions.add(new Detector.Condition(
                        matcher(owner, condition.terms(), condition.patterns(), condition.entities(), condition.sets()),
                        Detector.Scope.valueOf(condition.scope().toUpperCase(Locale.ROOT))));
            }
            return conditions;
        }

        private SpanMatcher matcher(String owner, List<String> terms, List<String> patterns, List<String> entities,
                                    List<String> setNames) {
            List<SpanMatcher> parts = new ArrayList<>();
            parts.add(new SpanMatcher(orEmpty(terms), patterns(owner, patterns), orEmpty(entities)));
            for (String name : orEmpty(setNames)) {
                parts.add(set(owner, name));
            }
            return parts.size() == 1 ? parts.getFirst() : SpanMatcher.union(parts);
        }

        private SpanMatcher set(String owner, String name) {
            SpanMatcher set = sets.get(name);
            require(set != null, owner + " refers to unknown set " + name);
            return set;
        }

        private static List<Pattern> patterns(String owner, List<String> sources) {
            List<Pattern> patterns = new ArrayList<>();
            List<String> list = orEmpty(sources);
            for (int i = 0; i < list.size(); i++) {
                patterns.add(PortableRegex.compile(RESOURCE, owner + " pattern " + i, list.get(i), null));
            }
            return patterns;
        }

        private void requireText(String key, String owner) {
            for (Lang lang : Lang.values()) {
                require(i18n.has(lang, key), owner + " needs catalogue text " + key + " in messages_" + lang.wire());
            }
        }
    }
}
