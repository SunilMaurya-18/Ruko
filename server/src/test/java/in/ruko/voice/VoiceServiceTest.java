package in.ruko.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.ruko.infra.RukoMetrics;
import in.ruko.infra.config.BhashiniProps;
import in.ruko.infra.config.VoiceProps;
import in.ruko.pipeline.Lang;
import in.ruko.support.BhashiniStub;
import in.ruko.support.Pipeline;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Every way TTS can fail ends in the browser_tts fallback and one count; good audio is cached. */
class VoiceServiceTest {

    private static final VoiceScripts SCRIPTS = new VoiceScripts(Pipeline.RULES, Pipeline.I18N, Pipeline.shippedSnapshot());
    private static final ScriptCounts COUNTS = new ScriptCounts(2, 1, 0);
    private static final BhashiniProps ON = props(Duration.ofMillis(300), "user", "key");

    private static BhashiniProps props(Duration timeout, String user, String key) {
        return new BhashiniProps(true, timeout, user, key, BhashiniProps.CONFIG_URL, BhashiniProps.PIPELINE_ID,
                Duration.ofSeconds(10));
    }

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final List<String> spoken = new ArrayList<>();

    @FunctionalInterface
    interface Speaker {
        byte[] speak(String text) throws Exception;
    }

    private VoiceService service(Speaker speaker, BhashiniProps props, CircuitBreaker breaker) {
        VoicePort port = new VoicePort() {
            @Override
            public byte[] synthesize(String text, Lang lang, Duration timeout) throws Exception {
                spoken.add(text);
                return speaker.speak(text);
            }

            @Override
            public String transcribe(byte[] wav, Lang lang, Duration timeout) {
                throw new UnsupportedOperationException();
            }
        };
        return new VoiceService(port, SCRIPTS, new TtsCache(), new AudioConverter(new VoiceProps("ffmpeg", 60,
                Duration.ofSeconds(10))), props, breaker, CircuitBreaker.ofDefaults("asr"), new RukoMetrics(registry));
    }

    private VoiceService service(Speaker speaker) {
        return service(speaker, ON, CircuitBreaker.ofDefaults("tts"));
    }

    private double fallbacks() {
        return registry.counter("ruko.tts.fallback").count();
    }

    private static void assertBrowserFallback(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(VoiceUnavailableException.class,
                e -> assertThat(e.fallback()).isEqualTo("browser_tts"));
    }

    @Test
    void speaksTheCatalogueScriptAndServesRepeatsFromTheCache() {
        VoiceService voice = service(text -> BhashiniStub.WAV);

        assertThat(voice.speak("band.high_concern", Lang.HI, COUNTS)).isEqualTo(BhashiniStub.WAV);
        assertThat(voice.speak("band.high_concern", Lang.HI, COUNTS)).isEqualTo(BhashiniStub.WAV);

        assertThat(spoken).containsExactly(SCRIPTS.build("band.high_concern", Lang.HI, COUNTS).text());
        assertThat(fallbacks()).isZero();
        assertThat(registry.find("ruko.tts.latency").timer().count()).isEqualTo(1);
    }

    @Test
    void notConfiguredFallsBackWithoutCallingBhashini() {
        VoiceService voice = service(text -> BhashiniStub.WAV, props(Duration.ofSeconds(3), "", ""),
                CircuitBreaker.ofDefaults("tts"));

        assertBrowserFallback(() -> voice.speak("voice.action", Lang.EN, COUNTS));
        assertThat(spoken).isEmpty();
        assertThat(fallbacks()).isEqualTo(1);
    }

    @Test
    void providerErrorFallsBack() {
        VoiceService voice = service(text -> {
            throw new IOException("Bhashini inference returned status 500");
        });

        assertBrowserFallback(() -> voice.speak("voice.action", Lang.HI, COUNTS));
        assertThat(fallbacks()).isEqualTo(1);
    }

    @Test
    void slowProviderFallsBackAtTheDeadline() {
        VoiceService voice = service(text -> {
            Thread.sleep(5_000);
            return BhashiniStub.WAV;
        });

        long started = System.nanoTime();
        assertBrowserFallback(() -> voice.speak("voice.action", Lang.HI, COUNTS));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
        assertThat(fallbacks()).isEqualTo(1);
    }

    @Test
    void nonWavReplyFallsBackAndIsNotCached() {
        AtomicInteger calls = new AtomicInteger();
        VoiceService voice = service(text -> {
            calls.incrementAndGet();
            return "<html>error</html>".getBytes();
        });

        assertBrowserFallback(() -> voice.speak("voice.action", Lang.HI, COUNTS));
        assertBrowserFallback(() -> voice.speak("voice.action", Lang.HI, COUNTS));
        assertThat(calls).hasValue(2);
        assertThat(fallbacks()).isEqualTo(2);
    }

    @Test
    void openCircuitSkipsBhashiniEntirely() {
        CircuitBreaker breaker = CircuitBreaker.of("tts", CircuitBreakerConfig.custom()
                .slidingWindowSize(2).minimumNumberOfCalls(2).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1)).build());
        VoiceService voice = service(text -> {
            throw new IOException("down");
        }, ON, breaker);

        assertBrowserFallback(() -> voice.speak("voice.action", Lang.HI, COUNTS));
        assertBrowserFallback(() -> voice.speak("voice.generic", Lang.HI, COUNTS));
        assertBrowserFallback(() -> voice.speak("footer.no_flags_not_safe", Lang.HI, COUNTS));

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(spoken).hasSize(2);
        assertThat(fallbacks()).isEqualTo(3);
    }

    @Test
    void unknownScriptIsRejectedBeforeAnythingElse() {
        VoiceService voice = service(text -> BhashiniStub.WAV);

        assertThatThrownBy(() -> voice.speak("sig.C1.reason", Lang.HI, COUNTS)).isInstanceOf(UnknownScriptException.class);
        assertThat(spoken).isEmpty();
        assertThat(fallbacks()).isZero();
    }
}
