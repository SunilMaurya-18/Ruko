package in.ruko.explain;

import java.time.Duration;

/**
 * A chat model with a strict JSON reply mode. Swappable: the provider is configuration. Implementations return the
 * model's reply text as-is (validation is {@link SchemaGuard}'s job), must give up after {@code timeout}, and must
 * never log the prompt or the reply.
 */
public interface LlmPort {

    String complete(LlmPrompt prompt, Duration timeout) throws Exception;
}
