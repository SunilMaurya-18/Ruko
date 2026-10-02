package in.ruko.infra.config;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("ruko.links")
public record LinksProps(@NotEmpty List<String> allow) {

    public LinksProps {
        allow = allow == null ? List.of() : List.copyOf(allow);
    }
}
