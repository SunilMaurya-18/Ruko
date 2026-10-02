package in.ruko.infra;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metric tags carry enums and route templates only, never message content. */
@Configuration(proxyBeanMethods = false)
public class MetricsConfig {

    private static final int MAX_URI_TAGS = 50;

    @Bean
    MeterRegistryCustomizer<MeterRegistry> rukoMeterRegistry() {
        return registry -> registry.config()
                .commonTags("application", "ruko")
                .meterFilter(MeterFilter.maximumAllowableTags(
                        "http.server.requests", "uri", MAX_URI_TAGS, MeterFilter.deny()));
    }
}
