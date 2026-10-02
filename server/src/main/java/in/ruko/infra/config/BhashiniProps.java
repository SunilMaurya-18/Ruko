package in.ruko.infra.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Bhashini (ULCA pipeline) credentials and endpoints. {@code timeout} bounds a whole TTS call, {@code asrTimeout} a
 * whole ASR call. The inference endpoint and key come back from the config call at {@code configUrl}.
 */
@Validated
@ConfigurationProperties("ruko.bhashini")
public record BhashiniProps(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("3s") @NotNull Duration timeout,
        @DefaultValue("") String userId,
        @DefaultValue("") String apiKey,
        @DefaultValue(CONFIG_URL) @NotBlank String configUrl,
        @DefaultValue(PIPELINE_ID) @NotBlank String pipelineId,
        @DefaultValue("10s") @NotNull Duration asrTimeout) {

    public static final String CONFIG_URL = "https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline";
    public static final String PIPELINE_ID = "64392f96daac500b55c543cd";

    public boolean configured() {
        return enabled && !userId.isBlank() && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "BhashiniProps[enabled=" + enabled + ", timeout=" + timeout + ", asrTimeout=" + asrTimeout
                + ", configured=" + configured() + "]";
    }
}
