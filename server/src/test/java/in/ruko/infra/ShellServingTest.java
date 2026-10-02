package in.ruko.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** The PWA build served on the API's origin, from a stand-in build under {@code shell-test/}. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.web.resources.static-locations=classpath:/shell-test/")
class ShellServingTest {

    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort
    int port;

    @Test
    void appRoutesGetTheShellWhichAlwaysRevalidates() throws Exception {
        for (String path : new String[] {"/", "/index.html", "/result", "/recovery", "/journal", "/how-ruko-decides",
                "/share?text=Guaranteed%20returns&title=x"}) {
            HttpResponse<String> response = get(path);
            assertThat(response.statusCode()).as(path).isEqualTo(200);
            assertThat(response.body()).as(path).contains("<title>shell-test</title>");
            assertThat(response.headers().firstValue("Content-Type")).as(path).hasValueSatisfying(
                    type -> assertThat(type).startsWith("text/html"));
            assertThat(response.headers().firstValue("Cache-Control")).as(path).hasValue("no-cache");
            assertThat(response.headers().firstValue("Content-Security-Policy")).as(path)
                    .hasValue(SecurityHeadersFilter.CSP);
        }
    }

    @Test
    void hashedAssetsAreImmutableAndTheServiceWorkerRevalidates() throws Exception {
        HttpResponse<String> asset = get("/assets/index-abc123.js");
        assertThat(asset.statusCode()).isEqualTo(200);
        assertThat(asset.headers().firstValue("Cache-Control")).hasValue("max-age=31536000, public, immutable");
        assertThat(asset.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).contains("javascript"));

        HttpResponse<String> worker = get("/sw.js");
        assertThat(worker.statusCode()).isEqualTo(200);
        assertThat(worker.headers().firstValue("Cache-Control")).hasValue("no-cache");

        HttpResponse<String> manifest = get("/manifest.webmanifest");
        assertThat(manifest.statusCode()).isEqualTo(200);
        assertThat(manifest.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/manifest+json"));
    }

    @Test
    void missingFilesAndServerPathsStayNotFound() throws Exception {
        for (String path : new String[] {"/missing.png", "/assets/missing-000.js", "/api/v1/journal",
                "/api/v1/journal/entries", "/api/v2/anything", "/actuator/nothing", "/v3/nothing"}) {
            HttpResponse<String> response = get(path);
            assertThat(response.statusCode()).as(path).isEqualTo(404);
            assertThat(response.body()).as(path).doesNotContain("shell-test");
            assertThat(response.headers().firstValue("Content-Type")).as(path).hasValueSatisfying(
                    type -> assertThat(type).startsWith("application/problem+json"));
        }
    }

    @Test
    void theApiIsUnchanged() throws Exception {
        HttpResponse<String> page = get("/api/v1/content/links?lang=en");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("scores.sebi.gov.in");
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
    }

    @Test
    void onlyAppRoutesFallBackToTheShell() {
        assertThat(ShellConfig.isAppRoute("")).isTrue();
        assertThat(ShellConfig.isAppRoute("share")).isTrue();
        assertThat(ShellConfig.isAppRoute("how-ruko-decides")).isTrue();
        assertThat(ShellConfig.isAppRoute("favicon.ico")).isFalse();
        assertThat(ShellConfig.isAppRoute("assets/x.js")).isFalse();
        assertThat(ShellConfig.isAppRoute("api/v1/journal")).isFalse();
        assertThat(ShellConfig.isAppRoute("actuator/health")).isFalse();
        assertThat(ShellConfig.isAppRoute("swagger-ui/index.html")).isFalse();
        assertThat(ShellConfig.isAppRoute("v3/api-docs")).isFalse();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
