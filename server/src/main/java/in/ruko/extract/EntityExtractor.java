package in.ruko.extract;

import in.ruko.extract.patterns.SharedPatterns;
import in.ruko.extract.patterns.SharedPatterns.EntityPattern;
import in.ruko.extract.patterns.SharedPatterns.EntityType;
import in.ruko.extract.patterns.SharedPatterns.Kind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Runs on normalised, masked text. Values are returned in order of first appearance, without duplicates. */
@Component
public class EntityExtractor {

    private static final Pattern PHONE_PLACEHOLDER = Pattern.compile("\\[PHONE]");

    private final List<EntityType> types;

    public EntityExtractor(SharedPatterns patterns) {
        this.types = patterns.entityTypes();
        for (EntityType type : types) {
            if (!Entities.TYPE_IDS.contains(type.id())) {
                throw new IllegalStateException(SharedPatterns.ENTITIES_RESOURCE + ": unknown entity type " + type.id());
            }
        }
    }

    public Entities extract(String text) {
        Map<String, List<String>> byType = new LinkedHashMap<>();
        for (EntityType type : types) {
            byType.put(type.id(), extract(type, text));
        }
        return Entities.of(byType, (int) PHONE_PLACEHOLDER.matcher(text).results().count());
    }

    private record Hit(int start, int end, String value) {
    }

    private static List<String> extract(EntityType type, String text) {
        List<Hit> hits = new ArrayList<>();
        for (EntityPattern pattern : type.patterns()) {
            Matcher matcher = pattern.pattern().matcher(text);
            while (matcher.find()) {
                if (matcher.end() > matcher.start()) {
                    hits.add(new Hit(matcher.start(), matcher.end(), pattern.value()));
                }
            }
        }
        hits.sort(Comparator.comparingInt(Hit::start).thenComparing(Comparator.comparingInt(Hit::end).reversed()));

        List<Hit> kept = new ArrayList<>();
        for (Hit hit : hits) {
            Hit last = kept.isEmpty() ? null : kept.getLast();
            if (type.kind() == Kind.PHRASE && last != null && hit.start() <= last.end() + 1) {
                kept.set(kept.size() - 1, new Hit(last.start(), Math.max(last.end(), hit.end()), null));
            } else if (last == null || hit.start() >= last.end()) {
                kept.add(hit);
            }
        }

        Set<String> values = new LinkedHashSet<>();
        for (Hit hit : kept) {
            String value = hit.value() != null ? hit.value() : text.substring(hit.start(), hit.end());
            value = trimTrailing(value, type.trim());
            value = switch (type.caseMode()) {
                case UPPER -> value.toUpperCase(Locale.ROOT);
                case LOWER -> value.toLowerCase(Locale.ROOT);
                case NONE -> value;
            };
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static String trimTrailing(String value, String chars) {
        int end = value.length();
        while (end > 0 && chars.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(0, end);
    }
}
