package in.ruko.infra;

import in.ruko.infra.config.BhashiniProps;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Circuit breakers for the external ports: after repeated failures or slow calls, skip the provider for a while and
 * use the deterministic fallback. TTS and ASR have separate breakers because ASR calls are slow by nature and a bad
 * upload must not silence the spoken result.
 */
@Configuration(proxyBeanMethods = false)
public class ResilienceConfig {

    public static final String LLM = "llm";
    public static final String BHASHINI_TTS = "bhashini-tts";
    public static final String BHASHINI_ASR = "bhashini-asr";

    @Bean
    CircuitBreaker llmCircuitBreaker() {
        return CircuitBreaker.of(LLM, base().slowCallDurationThreshold(Duration.ofSeconds(2)).build());
    }

    @Bean
    CircuitBreaker bhashiniTtsCircuitBreaker() {
        return CircuitBreaker.of(BHASHINI_TTS, base().slowCallDurationThreshold(Duration.ofSeconds(2)).build());
    }

    @Bean
    CircuitBreaker bhashiniAsrCircuitBreaker(BhashiniProps props) {
        return CircuitBreaker.of(BHASHINI_ASR, base().slowCallDurationThreshold(props.asrTimeout()).build());
    }

    private static CircuitBreakerConfig.Builder base() {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .slowCallRateThreshold(80)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(2);
    }
}
