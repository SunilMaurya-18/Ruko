package in.ruko.snapshot;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import in.ruko.infra.config.FeatureFlags;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Dated list of SEBI registration numbers, loaded once at startup. Stores numbers and the date only. An empty
 * file, or one without a date, is valid and means "no snapshot": U14 is still raised, S19 never is. The suspect
 * URL or number is never fetched or looked up anywhere else.
 */
@Component
public class SebiSnapshotIndex {

    public static final String RESOURCE = "snapshot/sebi-intermediaries.json";

    private static final Pattern REGISTRATION = Pattern.compile("IN[A-Z][0-9]{9}");
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private record SnapshotFile(String snapshotDate, List<String> registrationNumbers) {
    }

    private final LocalDate date;
    private final Set<String> numbers;

    @Autowired
    public SebiSnapshotIndex(FeatureFlags flags) {
        this(flags.snapshot() ? readResource() : new SnapshotFile(null, List.of()));
    }

    private SebiSnapshotIndex(SnapshotFile file) {
        Set<String> parsed = new HashSet<>();
        for (String number : file.registrationNumbers() == null ? List.<String>of() : file.registrationNumbers()) {
            String normalized = number == null ? "" : number.strip().toUpperCase(Locale.ROOT);
            if (!REGISTRATION.matcher(normalized).matches()) {
                throw new IllegalStateException(RESOURCE + ": malformed registration number at entry " + parsed.size());
            }
            parsed.add(normalized);
        }
        this.numbers = Set.copyOf(parsed);
        this.date = parseDate(file.snapshotDate());
    }

    public static SebiSnapshotIndex parse(String json) {
        try {
            return new SebiSnapshotIndex(MAPPER.readValue(json, SnapshotFile.class));
        } catch (IOException e) {
            throw new IllegalStateException(RESOURCE + ": not a valid snapshot file", e);
        }
    }

    /** True only for a dated, non-empty snapshot. */
    public boolean active() {
        return date != null && !numbers.isEmpty();
    }

    public Optional<LocalDate> date() {
        return active() ? Optional.of(date) : Optional.empty();
    }

    public int size() {
        return numbers.size();
    }

    /** Empty when there is no usable snapshot; otherwise listed or not listed. Never "verified". */
    public Optional<SnapshotStatus> lookup(String registrationNumber) {
        if (!active()) {
            return Optional.empty();
        }
        return Optional.of(numbers.contains(registrationNumber.toUpperCase(Locale.ROOT))
                ? SnapshotStatus.LISTED : SnapshotStatus.NOT_LISTED);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalStateException(RESOURCE + ": snapshot_date must be YYYY-MM-DD", e);
        }
    }

    private static SnapshotFile readResource() {
        try (InputStream in = SebiSnapshotIndex.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + ": missing from classpath");
            }
            return MAPPER.readValue(in, SnapshotFile.class);
        } catch (IOException e) {
            throw new UncheckedIOException(RESOURCE + " could not be read", e);
        }
    }
}
