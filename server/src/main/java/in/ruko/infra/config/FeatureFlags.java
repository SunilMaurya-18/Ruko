package in.ruko.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ruko.features")
public record FeatureFlags(
        @DefaultValue("false") boolean asr,
        @DefaultValue("true") boolean complaint,
        @DefaultValue("false") boolean s10DomainAge,
        @DefaultValue("true") boolean snapshot) {
}
