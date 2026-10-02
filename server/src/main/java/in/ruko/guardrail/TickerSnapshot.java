package in.ruko.guardrail;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Exchange ticker symbols for L2, from {@code snapshot/nse-bse-symbols.json}. Without a date the file is a hand-typed
 * seed list rather than an exchange snapshot; it is still used, and the date is reported as absent.
 */
@Component
public class TickerSnapshot {

    public static final String RESOURCE = "snapshot/nse-bse-symbols.json";

    private static final Pattern SYMBOL = Pattern.compile("[A-Z0-9&-]{1,20}");
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private record Source(String name, String url, String note) {
    }

    private record SymbolsFile(String snapshotDate, Source source, List<String> symbols) {
    }

    private final LocalDate date;
    private final Set<String> symbols;

    public TickerSnapshot() {
        this(read());
    }

    private TickerSnapshot(SymbolsFile file) {
        Set<String> parsed = new HashSet<>();
        for (String symbol : file.symbols() == null ? List.<String>of() : file.symbols()) {
            if (symbol == null || !SYMBOL.matcher(symbol).matches()) {
                throw new IllegalStateException(RESOURCE + ": malformed symbol at entry " + parsed.size());
            }
            parsed.add(symbol);
        }
        this.symbols = Set.copyOf(parsed);
        this.date = file.snapshotDate() == null ? null : LocalDate.parse(file.snapshotDate());
    }

    public static TickerSnapshot of(List<String> symbols) {
        return new TickerSnapshot(new SymbolsFile(null, null, symbols));
    }

    public boolean contains(String upperCaseSymbol) {
        return symbols.contains(upperCaseSymbol);
    }

    public Optional<LocalDate> date() {
        return Optional.ofNullable(date);
    }

    public int size() {
        return symbols.size();
    }

    private static SymbolsFile read() {
        try (InputStream in = TickerSnapshot.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + ": missing from classpath");
            }
            return MAPPER.readValue(in, SymbolsFile.class);
        } catch (IOException e) {
            throw new IllegalStateException(RESOURCE + ": not a valid symbols file", e);
        }
    }
}
