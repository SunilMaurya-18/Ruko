package in.ruko.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * S5: a registration-like number with a known prefix that has the wrong digit count, or whose prefix does not
 * match any role the message claims (for example "research analyst" with an INA number).
 */
final class PrefixMapDetector implements Detector {

    private static final int MIN_DIGITS = 4;
    private static final int MAX_DIGITS = 12;

    private final int digits;
    private final Map<String, SpanMatcher> rolesByPrefix;
    private final Pattern candidate;

    PrefixMapDetector(int digits, Map<String, SpanMatcher> rolesByPrefix) {
        this.digits = digits;
        this.rolesByPrefix = Map.copyOf(rolesByPrefix);
        this.candidate = Pattern.compile("(?<![\\p{L}\\p{M}\\p{N}])(" + String.join("|", rolesByPrefix.keySet())
                + ")([0-9]{" + MIN_DIGITS + "," + MAX_DIGITS + "})(?![\\p{L}\\p{M}\\p{N}])");
    }

    @Override
    public List<Found> find(RuleText text) {
        Set<String> claimed = new HashSet<>();
        rolesByPrefix.forEach((prefix, roles) -> {
            if (!roles.find(text).isEmpty()) {
                claimed.add(prefix);
            }
        });
        List<Found> found = new ArrayList<>();
        Matcher matcher = candidate.matcher(text.text());
        while (matcher.find()) {
            boolean malformed = matcher.group(2).length() != digits;
            boolean roleMismatch = !claimed.isEmpty() && !claimed.contains(matcher.group(1));
            if (malformed || roleMismatch) {
                found.add(new Found(matcher.start(), matcher.end(), matcher.group().toUpperCase(Locale.ROOT)));
            }
        }
        return found;
    }
}
