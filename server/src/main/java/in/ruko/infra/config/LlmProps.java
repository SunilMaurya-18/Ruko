package in.ruko.infra.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("ruko.llm")
public record LlmProps(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("3s") @NotNull Duration timeout,
        @DefaultValue("") String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("") String model) {

    public boolean configured() {
        return enabled && !baseUrl.isBlank() && !apiKey.isBlank() && !model.isBlank();
    }

    @Override
    public String toString() {
        return "LlmProps[enabled=" + enabled + ", timeout=" + timeout + ", configured=" + configured() + "]";
    }
}
