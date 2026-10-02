package in.ruko.infra.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("ruko.rate-limit")
public record RateLimitProps(@DefaultValue("30") @Positive int perIpPerMin) {
}
