package in.ruko.infra;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the PWA build (packaged under {@code static/} with {@code -Dpwa.dist}) on the API's origin. Hashed
 * {@code /assets/} files are cached for a year; everything else, including {@code index.html} and {@code sw.js},
 * revalidates so a deploy reaches phones on the next visit. A path that is an app route (no file extension, not a
 * server path) gets {@code index.html}; anything else that does not exist stays a 404 problem, so
 * {@code /api/v1/journal} is still not found. Spring's default static mapping is off
 * ({@code spring.web.resources.add-mappings: false}) so this is the only one.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ShellConfig implements WebMvcConfigurer {

    static final String SHELL = "index.html";
    private static final List<String> SERVER_PREFIXES = List.of("api/", "actuator/", "swagger-ui", "v3/", "webjars/");

    private final String[] locations;

    public ShellConfig(WebProperties web) {
        this.locations = web.getResources().getStaticLocations();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(Arrays.stream(locations).map(location -> location + "assets/").toArray(String[]::new))
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/**")
                .addResourceLocations(locations)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new ShellResolver());
    }

    static boolean isAppRoute(String path) {
        if (path.isEmpty()) {
            return true;
        }
        if (SERVER_PREFIXES.stream().anyMatch(path::startsWith)) {
            return false;
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        return !last.contains(".");
    }

    static final class ShellResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource found = resourcePath.isEmpty() ? null : super.getResource(resourcePath, location);
            if (found == null && isAppRoute(resourcePath)) {
                return super.getResource(SHELL, location);
            }
            return found;
        }
    }
}
