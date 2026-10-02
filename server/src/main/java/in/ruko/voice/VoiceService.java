package in.ruko.voice;

import static in.ruko.infra.SafeLog.errorType;
import static in.ruko.infra.SafeLog.tag;

import in.ruko.infra.LogEvent;
import in.ruko.infra.LogKey;
import in.ruko.infra.RukoMetrics;
import in.ruko.infra.SafeLog;
import in.ruko.infra.config.BhashiniProps;
import in.ruko.pipeline.Lang;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * TTS and ASR with a deterministic fallback. TTS speaks a {@link VoiceScripts} key only, from {@link TtsCache} when it
 * can; otherwise one Bhashini call under the {@code ruko.bhashini.timeout} deadline and a circuit breaker. Any failure,
 * including "not configured", is {@code ruko_tts_fallback_total++} and a 503 telling the PWA to use
 * {@code speechSynthesis}. Neither the script nor the transcript is ever logged.
 */
@Component
public class VoiceService implements DisposableBean {

    private static final SafeLog LOG = SafeLog.of(VoiceService.class);

    private final VoicePort port;
    private final VoiceScripts scripts;
    private final TtsCache cache;
    private final AudioConverter converter;
    private final BhashiniProps props;
    private final CircuitBreaker ttsBreaker;
    private final CircuitBreaker asrBreaker;
    private final RukoMetrics metrics;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public VoiceService(VoicePort port, VoiceScripts scripts, TtsCache cache, AudioConverter converter,
                        BhashiniProps props,
                        @Qualifier("bhashiniTtsCircuitBreaker") CircuitBreaker ttsBreaker,
                        @Qualifier("bhashiniAsrCircuitBreaker") CircuitBreaker asrBreaker,
                        RukoMetrics metrics) {
        this.port = port;
        this.scripts = scripts;
        this.cache = cache;
        this.converter = converter;
        this.props = props;
        this.ttsBreaker = ttsBreaker;
        this.asrBreaker = asrBreaker;
        this.metrics = metrics;
    }

    /**
     * @return WAV audio for the script
     * @throws UnknownScriptException the key is not a spoken script
     * @throws VoiceUnavailableException with {@code browser_tts} when Bhashini cannot serve it
     */
    public byte[] speak(String scriptKey, Lang lang, ScriptCounts counts) {
        VoiceScripts.Script script = scripts.build(scriptKey, lang, counts);
        Optional<byte[]> cached = cache.get(script.cacheKey());
        if (cached.isPresent()) {
            return cached.get();
        }
        if (!props.configured()) {
            throw ttsFallback(VoiceFallback.NOT_CONFIGURED, null);
        }
        long started = System.nanoTime();
        byte[] audio;
        try {
            audio = ttsBreaker.executeCallable(
                    () -> withDeadline(() -> port.synthesize(script.text(), lang, props.timeout()), props.timeout()));
        } catch (CallNotPermittedException e) {
            throw ttsFallback(VoiceFallback.CIRCUIT_OPEN, null);
        } catch (TimeoutException e) {
            throw ttsFallback(VoiceFallback.TIMEOUT, null);
        } catch (Exception e) {
            throw ttsFallback(VoiceFallback.ERROR, e);
        } finally {
            metrics.ttsTook(Duration.ofNanos(System.nanoTime() - started));
        }
        if (!Wav.isWav(audio)) {
            throw ttsFallback(VoiceFallback.BAD_AUDIO, null);
        }
        cache.put(script.cacheKey(), audio);
        return audio;
    }

    /**
     * @throws AudioRejectedException the upload could not be decoded
     * @throws VoiceUnavailableException ffmpeg or Bhashini could not serve it
     */
    public String transcribe(byte[] upload, Lang lang) {
        if (!props.configured()) {
            throw asrFailure(VoiceFallback.NOT_CONFIGURED, null);
        }
        byte[] wav;
        try {
            wav = converter.toWav(upload);
        } catch (AudioRejectedException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw asrFailure(VoiceFallback.CONVERTER, e);
        } catch (Exception e) {
            throw asrFailure(VoiceFallback.CONVERTER, e);
        }
        try {
            return asrBreaker.executeCallable(
                    () -> withDeadline(() -> port.transcribe(wav, lang, props.asrTimeout()), props.asrTimeout()));
        } catch (CallNotPermittedException e) {
            throw asrFailure(VoiceFallback.CIRCUIT_OPEN, null);
        } catch (TimeoutException e) {
            throw asrFailure(VoiceFallback.TIMEOUT, null);
        } catch (Exception e) {
            throw asrFailure(VoiceFallback.ERROR, e);
        }
    }

    private <T> T withDeadline(Callable<T> call, Duration timeout) throws Exception {
        Future<T> future = executor.submit(call);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    private VoiceUnavailableException ttsFallback(VoiceFallback reason, Exception error) {
        metrics.ttsFallback();
        log(LogEvent.TTS_FALLBACK, reason, error);
        return new VoiceUnavailableException(VoiceUnavailableException.BROWSER_TTS);
    }

    private VoiceUnavailableException asrFailure(VoiceFallback reason, Exception error) {
        log(LogEvent.ASR_FAILED, reason, error);
        return new VoiceUnavailableException(null);
    }

    private static void log(LogEvent event, VoiceFallback reason, Exception error) {
        if (error == null) {
            LOG.warn(event, tag(LogKey.REASON, reason));
        } else {
            LOG.warn(event, tag(LogKey.REASON, reason), errorType(error));
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
