package in.ruko.voice;

import in.ruko.pipeline.Lang;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * In-memory LRU of synthesised audio, keyed by (script key, language, counts). Safe to keep because the input is
 * catalogue text only (see {@link VoiceScripts}); no request text can become a key or a value. Bounded by entries
 * and bytes.
 */
@Component
public class TtsCache {

    static final int MAX_ENTRIES = 256;
    static final long MAX_BYTES = 24L * 1024 * 1024;

    /** {@code counts} is null for scripts whose words do not depend on them. */
    public record Key(String scriptKey, Lang lang, ScriptCounts counts) {
    }

    private final int maxEntries;
    private final long maxBytes;
    private final LinkedHashMap<Key, byte[]> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long bytes;

    public TtsCache() {
        this(MAX_ENTRIES, MAX_BYTES);
    }

    TtsCache(int maxEntries, long maxBytes) {
        this.maxEntries = maxEntries;
        this.maxBytes = maxBytes;
    }

    public synchronized Optional<byte[]> get(Key key) {
        return Optional.ofNullable(entries.get(key));
    }

    public synchronized void put(Key key, byte[] audio) {
        if (audio.length > maxBytes) {
            return;
        }
        byte[] previous = entries.put(key, audio);
        bytes += audio.length - (previous == null ? 0 : previous.length);
        Iterator<Map.Entry<Key, byte[]>> eldest = entries.entrySet().iterator();
        while ((entries.size() > maxEntries || bytes > maxBytes) && eldest.hasNext()) {
            bytes -= eldest.next().getValue().length;
            eldest.remove();
        }
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized long bytes() {
        return bytes;
    }
}
