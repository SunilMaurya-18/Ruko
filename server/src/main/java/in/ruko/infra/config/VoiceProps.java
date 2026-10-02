package in.ruko.infra.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** ffmpeg for ASR uploads only: the binary, how much audio is kept, and how long a conversion may run. */
@Validated
@ConfigurationProperties("ruko.voice")
public record VoiceProps(
        @DefaultValue("ffmpeg") @NotBlank String ffmpeg,
        @DefaultValue("60") @Min(1) @Max(120) int asrMaxSeconds,
        @DefaultValue("10s") @NotNull Duration conversionTimeout) {
}
