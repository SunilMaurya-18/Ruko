package in.ruko.submission;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.ruko.guardrail.OutboundLinkPolicy;
import in.ruko.support.CanaryProbeController;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Submission pack: {@code docs/submission/openapi.json} from the running server, and {@code link-audit.md}, every
 * link in the shipped app, content, and server config, classified and checked against {@link OutboundLinkPolicy}.
 * Only runs with {@code ./mvnw -Psubmission test}. Fails if a link a user can see is off the allowlist.
 */
@Tag("submission")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SubmissionPackTest {

    private static final Path ROOT = Path.of("..");
    private static final Path DIR = ROOT.resolve("docs/submission");
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();

    private static final Pattern ANY_LINK = Pattern.compile("(?:https?://|tel:|www\\.)[^\\s\"'<>)\\]`,;{}]+");
    private static final Pattern WEB_LINK = Pattern.compile("https?://[^\\s\"'<>)\\]`,;{}]+");

    enum Kind {
        USER("shown to users", ANY_LINK),
        SERVER("server-side call, never shown", WEB_LINK),
        PROVENANCE("data source or schema id, never fetched at runtime", WEB_LINK);

        final String label;
        final Pattern pattern;

        Kind(String label, Pattern pattern) {
            this.label = label;
            this.pattern = pattern;
        }
    }

    private record Scan(String path, Kind kind) {
    }

    private static final List<Scan> SCANS = List.of(
            new Scan("shared/content", Kind.USER),
            new Scan("shared/i18n", Kind.USER),
            new Scan("pwa/src", Kind.USER),
            new Scan("pwa/sw", Kind.USER),
            new Scan("pwa/index.html", Kind.USER),
            new Scan("pwa/public/manifest.webmanifest", Kind.USER),
            new Scan("server/src/main/resources", Kind.SERVER),
            new Scan("server/src/main/java", Kind.SERVER),
            new Scan("shared/rules", Kind.PROVENANCE),
            new Scan("shared/snapshot", Kind.PROVENANCE),
            new Scan("shared/schemas", Kind.PROVENANCE));

    private record Found(String link, Kind kind, String where) {
    }

    @LocalServerPort
    int port;

    @Autowired
    OutboundLinkPolicy links;

    @Test
    void writeOpenApi() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");
        assertThat(response.statusCode()).isEqualTo(200);
        ObjectNode doc = (ObjectNode) JSON.readTree(response.body());
        ObjectNode paths = (ObjectNode) doc.path("paths");
        new ArrayList<>(paths.properties().stream().map(Map.Entry::getKey).toList()).stream()
                .filter(path -> path.contains(CanaryProbeController.PATH)).forEach(paths::remove);
        dropUnreferencedSchemas(doc);
        doc.putArray("servers").addObject().put("url", "/").put("description", "Same origin as the PWA");

        Files.createDirectories(DIR);
        Files.writeString(DIR.resolve("openapi.json"), JSON.writeValueAsString(doc) + "\n", StandardCharsets.UTF_8);
        assertThat(paths.size()).isEqualTo(7);
    }

    @Test
    void writeLinkAudit() throws Exception {
        List<Found> found = new ArrayList<>();
        for (Scan scan : SCANS) {
            for (Path file : files(ROOT.resolve(scan.path()))) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher m = scan.kind().pattern.matcher(lines.get(i));
                    while (m.find()) {
                        String link = m.group().replaceAll("[.:]+$", "");
                        if (scan.kind() == Kind.USER && isDocComment(lines.get(i))) {
                            continue;
                        }
                        found.add(new Found(link, scan.kind(), ROOT.relativize(file).toString().replace('\\', '/') + ":" + (i + 1)));
                    }
                }
            }
        }

        Map<String, List<Found>> byLink = new TreeMap<>();
        found.forEach(f -> byLink.computeIfAbsent(f.kind().ordinal() + " " + f.link(), k -> new ArrayList<>()).add(f));

        JsonNode served = JSON.readTree(get("/api/v1/content/links?lang=en").body());
        List<String> servedUrls = new ArrayList<>();
        served.path("links").forEach(link -> servedUrls.add(link.path("url").asText()));

        List<String> refused = new ArrayList<>();
        StringBuilder md = new StringBuilder();
        String checkedAt = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"));
        md.append("# Link audit\n\n")
                .append("Generated by `cd server && ./mvnw -Psubmission test` (`SubmissionPackTest`). Do not edit by hand.\n\n")
                .append("| | |\n| --- | --- |\n")
                .append("| Generated | ").append(checkedAt).append(" |\n")
                .append("| Allowlist (`ruko.links.allow`) | ").append(String.join(", ", new TreeSet<>(links.hosts())))
                .append(", plus `tel:1930` |\n")
                .append("| Policy | `OutboundLinkPolicy`: HTTPS on an allowlisted host, port 443, no user info; or `tel:1930`. ")
                .append("The server refuses to start if `shared/content/links.v0.json` breaks it, and linter check L5 ")
                .append("blocks any other link in a response. |\n")
                .append("| Scanned | ").append(String.join(", ", SCANS.stream().map(s -> "`" + s.path() + "`").toList()))
                .append(" (tests and labelled sample messages excluded) |\n\n");

        md.append("## Links\n\n| Link | Kind | Policy | Reachable at generation | Found in |\n| --- | --- | --- | --- | --- |\n");
        for (List<Found> group : byLink.values()) {
            Found first = group.getFirst();
            String policy;
            String reachable = "";
            if (first.kind() == Kind.USER) {
                boolean allowed = links.allows(first.link());
                policy = allowed ? "allowlisted" : "**not allowlisted**";
                if (!allowed) {
                    refused.add(first.link());
                }
                reachable = first.link().startsWith("tel:") ? "phone action" : reach(first.link());
            } else {
                policy = first.kind().label;
            }
            md.append("| `").append(first.link()).append("` | ").append(first.kind() == Kind.USER ? "user-facing" : first.kind().name().toLowerCase())
                    .append(" | ").append(policy).append(" | ").append(reachable).append(" | ")
                    .append(String.join("<br>", group.stream().map(Found::where).distinct().limit(4).toList()))
                    .append(group.size() > 4 ? "<br>…" : "").append(" |\n");
        }

        md.append("\n## What the API hands the app\n\n`GET /api/v1/content/links` returns: ")
                .append(String.join(", ", servedUrls.stream().map(u -> "`" + u + "`").toList()))
                .append(servedUrls.stream().allMatch(links::allows) ? ". All allowlisted.\n" : ". **Not all allowlisted.**\n")
                .append("\n## Links inside a checked message\n\n")
                .append("A URL in a forwarded message is extracted as text (`entities.urls`) so the user can see it. ")
                .append("Ruko never fetches or resolves it, and check L5 stops a response from turning it into a link. ")
                .append("Domain-age lookup (S10, RDAP) is off. The PWA's Content-Security-Policy is `connect-src 'self'`, ")
                .append("so the app cannot call any other origin.\n");

        Files.createDirectories(DIR);
        Files.writeString(DIR.resolve("link-audit.md"), md.toString(), StandardCharsets.UTF_8);

        assertThat(refused).as("user-facing links off the allowlist").isEmpty();
        assertThat(servedUrls).allMatch(links::allows);
        assertThat(byLink.keySet()).anyMatch(key -> key.contains("tel:1930"));
    }

    /** The test-only canary route is gone from the paths; its request schema goes too. */
    private static void dropUnreferencedSchemas(ObjectNode doc) {
        ObjectNode schemas = (ObjectNode) doc.path("components").path("schemas");
        Pattern ref = Pattern.compile("#/components/schemas/([A-Za-z0-9_.-]+)");
        TreeSet<String> used = new TreeSet<>();
        Matcher fromPaths = ref.matcher(doc.path("paths").toString());
        while (fromPaths.find()) {
            used.add(fromPaths.group(1));
        }
        boolean grew = true;
        while (grew) {
            grew = false;
            for (String name : List.copyOf(used)) {
                Matcher nested = ref.matcher(schemas.path(name).toString());
                while (nested.find()) {
                    grew |= used.add(nested.group(1));
                }
            }
        }
        new ArrayList<>(schemas.properties().stream().map(Map.Entry::getKey).toList()).stream()
                .filter(name -> !used.contains(name)).forEach(schemas::remove);
    }

    private static boolean isDocComment(String line) {
        String trimmed = line.strip();
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
    }

    private static List<Path> files(Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            return List.of(path);
        }
        try (Stream<Path> walk = Files.walk(path)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().contains(".test."))
                    .sorted().toList();
        }
    }

    private static String reach(String link) {
        try {
            HttpResponse<Void> response = CLIENT.send(HttpRequest.newBuilder(URI.create(link)).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Ruko link audit").GET().build(), HttpResponse.BodyHandlers.discarding());
            return "HTTP " + response.statusCode();
        } catch (Exception e) {
            return "no answer (" + e.getClass().getSimpleName() + ")";
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
