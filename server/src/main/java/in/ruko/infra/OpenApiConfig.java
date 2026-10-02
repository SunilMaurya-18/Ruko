package in.ruko.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The OpenAPI document ({@code /v3/api-docs}, page at {@code /swagger-ui.html}), limited to {@code /api/**}. */
@Configuration
public class OpenApiConfig {

    static final String DESCRIPTION = """
            Stateless analysis, voice, and content service behind the Ruko PWA. Request text lives for one request: \
            it is never stored or logged. Rules decide the band; an optional LLM may only add tags and card text. \
            Errors are RFC 9457 problem details that never echo input.

            Independent prototype. Not an official SEBI or NSDL product, and not investment advice. DPDP principles \
            applied; this is not legal advice.""";

    @Bean
    OpenAPI rukoOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Ruko API")
                .version("v1")
                .description(DESCRIPTION)
                .license(new License().name("Prototype, no warranty")));
    }

    /** Schemas use the API's own Jackson settings, so field names come out snake_case as on the wire. */
    @Bean
    ModelResolver modelResolver(ObjectMapper objectMapper) {
        return new ModelResolver(objectMapper);
    }
}
