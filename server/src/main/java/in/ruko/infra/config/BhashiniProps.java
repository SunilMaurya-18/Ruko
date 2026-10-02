package in.ruko.infra.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("ruko.bhashini")
public record BhashiniProps(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("3s") @NotNull Duration timeout,
        @DefaultValue("") String userId,
        @DefaultValue("") String apiKey) {

    public boolean configured() {
        return enabled && !userId.isBlank() && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "BhashiniProps[enabled=" + enabled + ", timeout=" + timeout + ", configured=" + configured() + "]";
    }
}
