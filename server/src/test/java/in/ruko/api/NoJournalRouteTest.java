package in.ruko.api;

import static org.assertj.core.api.Assertions.assertThat;

import in.ruko.support.CanaryProbeController;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Track D: the pause journal lives in the phone's localStorage only. The API surface is pinned here, so a route that
 * could take journal text (or any new route) fails this test until it is reviewed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "ruko.rate-limit.per-ip-per-min=1000")
class NoJournalRouteTest {

    private static final Set<String> API = Set.of(
            "POST /api/v1/analyze",
            "POST /api/v1/voice/tts",
            "POST /api/v1/voice/asr",
            "GET /api/v1/content/how-ruko-decides",
            "GET /api/v1/content/recovery",
            "GET /api/v1/content/links",
            "POST /api/v1/complaint/draft");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @LocalServerPort
    int port;

    @Test
    void theApiSurfaceHasNoJournalRoute() {
        Set<String> routes = new TreeSet<>();
        mappings.getHandlerMethods().keySet().forEach(info -> info.getPatternValues().forEach(pattern ->
                info.getMethodsCondition().getMethods().forEach(method -> routes.add(method + " " + pattern))));
        Set<String> api = new TreeSet<>(routes.stream()
                .filter(route -> route.contains(" /api/") && !route.contains(CanaryProbeController.PATH)).toList());

        assertThat(api).isEqualTo(new TreeSet<>(API));
        assertThat(routes).noneMatch(route -> route.toLowerCase(Locale.ROOT).contains("journal"));
    }

    @Test
    void journalPathsAreNotFound() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        for (String path : new String[] {"/api/v1/journal", "/api/v1/content/journal", "/api/v1/journal/entries"}) {
            URI uri = URI.create("http://localhost:" + port + path);
            HttpResponse<String> post = client.send(HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"why\": \"x\"}")).build(), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> get = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(post.statusCode()).as("POST " + path).isEqualTo(404);
            assertThat(get.statusCode()).as("GET " + path).isEqualTo(404);
        }
    }
}
