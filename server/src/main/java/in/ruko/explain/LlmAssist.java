package in.ruko.explain;

import static in.ruko.infra.SafeLog.errorType;
import static in.ruko.infra.SafeLog.tag;

import in.ruko.infra.LogEvent;
import in.ruko.infra.LogKey;
import in.ruko.infra.RukoMetrics;
import in.ruko.infra.SafeLog;
import in.ruko.infra.config.LlmProps;
import in.ruko.pipeline.Lang;
import in.ruko.rules.LlmTag;
import in.ruko.rules.RuleLoader;
import in.ruko.rules.RuleSet;
import in.ruko.rules.RuleText;
import in.ruko.rules.SignalHit;
import in.ruko.rules.SignalRule;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 * The optional LLM garnish: one call per request, temperature 0, hard timeout, circuit breaker. The reply must pass
 * {@link SchemaGuard}; every tag must be an enabled LLM-tag rule with a span copied verbatim from the input; cards
 * may only be for fired or taggable signals. Any failure means an empty assist and {@code ruko_llm_fallback_total++}.
 * When the LLM is not configured this returns empty without counting a fallback.
 */
@Component
public class LlmAssist implements DisposableBean {

    private static final SafeLog LOG = SafeLog.of(LlmAssist.class);

    private final LlmPort port;
    private final PromptFactory prompts;
    private final SchemaGuard guard;
    private final LlmProps props;
    private final RuleSet rules;
    private final CircuitBreaker breaker;
    private final RukoMetrics metrics;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public LlmAssist(LlmPort port, PromptFactory prompts, SchemaGuard guard, LlmProps props, RuleLoader rules,
                     @Qualifier("llmCircuitBreaker") CircuitBreaker llmCircuitBreaker, RukoMetrics metrics) {
        this.port = port;
        this.prompts = prompts;
        this.guard = guard;
        this.props = props;
        this.rules = rules.ruleSet();
        this.breaker = llmCircuitBreaker;
        this.metrics = metrics;
    }

    public Assist assist(RuleText text, Lang lang, List<SignalHit> hits) {
        if (!props.configured()) {
            return Assist.EMPTY;
        }
        String input = text.quote(0, text.length());
        LlmPrompt prompt = prompts.build(input, lang, hits);
        long started = System.nanoTime();
        String reply;
        try {
            reply = breaker.executeCallable(() -> call(prompt));
        } catch (CallNotPermittedException e) {
            return fallback(LlmFallback.CIRCUIT_OPEN, null);
        } catch (TimeoutException e) {
            return fallback(LlmFallback.TIMEOUT, null);
        } catch (Exception e) {
            return fallback(LlmFallback.ERROR, e);
        } finally {
            metrics.llmTook(Duration.ofNanos(System.nanoTime() - started));
        }

        Optional<Assist> parsed = guard.parse(reply);
        if (parsed.isEmpty()) {
            return fallback(LlmFallback.SCHEMA, null);
        }
        Assist assist = parsed.get();
        Set<String> cardable = new HashSet<>();
        hits.forEach(hit -> cardable.add(hit.id()));
        for (LlmTag tag : assist.tags()) {
            Optional<SignalRule> rule = rules.rule(tag.id());
            if (rule.isEmpty() || !rule.get().enabled() || !rule.get().llmTag()) {
                return fallback(LlmFallback.TAG, null);
            }
            if (tag.span().isBlank() || !input.contains(tag.span().strip())) {
                return fallback(LlmFallback.SPAN, null);
            }
            cardable.add(tag.id());
        }
        if (!cardable.containsAll(assist.cards().keySet())) {
            return fallback(LlmFallback.CARD, null);
        }
        return assist;
    }

    private String call(LlmPrompt prompt) throws Exception {
        Duration timeout = props.timeout();
        Future<String> future = executor.submit(() -> port.complete(prompt, timeout));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    private Assist fallback(LlmFallback reason, Exception error) {
        metrics.llmFallback();
        if (error == null) {
            LOG.warn(LogEvent.LLM_FALLBACK, tag(LogKey.REASON, reason));
        } else {
            LOG.warn(LogEvent.LLM_FALLBACK, tag(LogKey.REASON, reason), errorType(error));
        }
        return Assist.EMPTY;
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
