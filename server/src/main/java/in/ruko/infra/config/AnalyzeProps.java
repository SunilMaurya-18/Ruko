package in.ruko.infra.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("ruko.analyze")
public record AnalyzeProps(
        @DefaultValue("4000") @Positive int maxChars,
        @DefaultValue("20") @Positive int minChars,
        @DefaultValue("0.6") @DecimalMin("0.0") @DecimalMax("1.0") double minOcrConfidence,
        @DefaultValue("65536") @Positive int maxBodyBytes) {
}
