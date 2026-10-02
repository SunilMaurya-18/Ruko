package in.ruko.voice;

import static org.assertj.core.api.Assertions.assertThat;

import in.ruko.pipeline.Lang;
import org.junit.jupiter.api.Test;

class TtsCacheTest {

    private static TtsCache.Key key(String script) {
        return new TtsCache.Key(script, Lang.HI, null);
    }

    @Test
    void evictsLeastRecentlyUsedBeyondTheEntryLimit() {
        TtsCache cache = new TtsCache(2, 1_000);
        cache.put(key("a"), new byte[10]);
        cache.put(key("b"), new byte[10]);
        cache.get(key("a"));
        cache.put(key("c"), new byte[10]);

        assertThat(cache.get(key("a"))).isPresent();
        assertThat(cache.get(key("b"))).isEmpty();
        assertThat(cache.get(key("c"))).isPresent();
        assertThat(cache.bytes()).isEqualTo(20);
    }

    @Test
    void staysWithinTheByteBudget() {
        TtsCache cache = new TtsCache(100, 25);
        cache.put(key("a"), new byte[10]);
        cache.put(key("b"), new byte[10]);
        cache.put(key("c"), new byte[10]);

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.bytes()).isEqualTo(20);
        assertThat(cache.get(key("a"))).isEmpty();
    }

    @Test
    void ignoresAudioLargerThanTheWholeBudgetAndCountsReplacementsOnce() {
        TtsCache cache = new TtsCache(100, 25);
        cache.put(key("huge"), new byte[26]);
        cache.put(key("a"), new byte[10]);
        cache.put(key("a"), new byte[5]);

        assertThat(cache.get(key("huge"))).isEmpty();
        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.bytes()).isEqualTo(5);
    }

    @Test
    void countsAreAnotherKey() {
        TtsCache cache = new TtsCache(10, 1_000);
        cache.put(new TtsCache.Key("band.high_concern", Lang.HI, new ScriptCounts(1, 0, 0)), new byte[1]);

        assertThat(cache.get(new TtsCache.Key("band.high_concern", Lang.HI, new ScriptCounts(1, 0, 0)))).isPresent();
        assertThat(cache.get(new TtsCache.Key("band.high_concern", Lang.HI, new ScriptCounts(2, 0, 0)))).isEmpty();
        assertThat(cache.get(new TtsCache.Key("band.high_concern", Lang.EN, new ScriptCounts(1, 0, 0)))).isEmpty();
    }
}
