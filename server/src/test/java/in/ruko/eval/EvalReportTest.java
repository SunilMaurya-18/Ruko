package in.ruko.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.ruko.api.dto.AnalyzeRequest;
import in.ruko.guardrail.LintResult;
import in.ruko.pipeline.Lang;
import in.ruko.rules.RuleType;
import in.ruko.rules.SignalRule;
import in.ruko.snapshot.SebiSnapshotIndex;
import in.ruko.support.Canary;
import in.ruko.support.Pipeline;
import in.ruko.support.Pipeline.Fixture;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Phase 6 evaluation: runs the labelled fixtures against the real server (LLM off, shipped snapshot) and writes
 * {@code docs/eval-report.md}. Only runs with {@code ./mvnw -Peval test}. The report is written before the hard
 * gates are asserted (linter 100%, zero log leaks, payload budget, server/on-device parity when Node is present),
 * so a failing run still leaves the numbers behind. Detection targets are reported, not asserted.
 */
@Tag("eval")
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "ruko.rate-limit.per-ip-per-min=1000000")
class EvalReportTest {

    private static final Path REPORT = Path.of("..", "docs", "eval-report.md");
    private static final Path PWA = Path.of("..", "pwa");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final double DETECTION_TARGET = 0.90;
    private static final double EDUCATION_FLAGGED_TARGET = 0.10;
    private static final long P95_TARGET_MS = 1500;
    private static final int MAX_GZIPPED_BYTES = 6 * 1024;
    private static final int WARMUP = 200;
    private static final int LOAD_REQUESTS = 1000;
    private static final int LOAD_THREADS = 8;
    private static final Set<String> FLAGGED = Set.of("some_concern", "high_concern");
    private static final Set<String> LLM_ONLY = Pipeline.RULES.ruleSet().rules().stream()
            .filter(rule -> rule.type() == RuleType.LLM_TAG).map(SignalRule::id).collect(Collectors.toSet());

    @LocalServerPort
    int port;

    @Autowired
    MeterRegistry registry;

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Test
    void writeReport(CapturedOutput output) throws Exception {
        URI base = URI.create("http://localhost:" + port);
        List<Fixture> fixtures = Pipeline.fixtures();
        StringBuilder md = new StringBuilder();

        Map<String, JsonNode> server = new LinkedHashMap<>();
        List<Integer> payloads = new ArrayList<>();
        for (Fixture fixture : fixtures) {
            HttpResponse<String> response = post(base, body(fixture, ""));
            assertThat(response.statusCode()).as(fixture.id()).isEqualTo(200);
            server.put(fixture.id(), JSON.readTree(response.body()));
            payloads.add(gzipped(response.body()));
        }

        header(md, fixtures);
        boolean detectionMet = detection(md, fixtures, server);
        Parity parity = parity(md, fixtures, server);
        Lint lint = linter(md, fixtures);
        long[] latency = latency(md, base, fixtures);
        int maxPayload = payload(md, payloads);
        int leaks = logAudit(md, base, fixtures, output);
        notMeasuredHere(md);
        summary(md, detectionMet, parity, fixtures.size(), lint, latency, maxPayload, leaks);

        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, md.toString(), StandardCharsets.UTF_8);
        System.out.println("Wrote " + REPORT.toAbsolutePath().normalize());

        assertThat(lint.passed()).as("linter pass rate").isEqualTo(lint.total());
        assertThat(lint.served()).as("ruko_lint_fail_total").isZero();
        assertThat(leaks).as("canary in logs").isZero();
        assertThat(maxPayload).as("largest gzipped result").isLessThanOrEqualTo(MAX_GZIPPED_BYTES);
        if (parity.ran()) {
            assertThat(parity.full()).as("server/on-device full agreement").isEqualTo(fixtures.size());
        }
    }

    private void header(StringBuilder md, List<Fixture> fixtures) {
        Map<String, Long> kinds = fixtures.stream()
                .collect(Collectors.groupingBy(Fixture::kind, TreeMap::new, Collectors.counting()));
        md.append("# Ruko evaluation report\n\n")
                .append("Generated by `cd server && ./mvnw -Peval test` (`EvalReportTest`). Do not edit by hand.\n\n")
                .append("| | |\n| --- | --- |\n")
                .append("| Generated | ").append(ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"))).append(" |\n")
                .append("| Fixtures | ").append(fixtures.size()).append(" (")
                .append(kinds.entrySet().stream().map(e -> e.getValue() + " " + e.getKey())
                        .collect(Collectors.joining(", "))).append(") |\n")
                .append("| Fixtures with a known gap | ").append(fixtures.stream().filter(Fixture::hasKnownGap).count())
                .append(" (labels come from the signal definitions, not from the rules' output) |\n")
                .append("| Caveat | the fixtures are also the development set: rules have been fixed against some of them, ")
                .append("so these numbers are optimistic for messages Ruko has never seen |\n")
                .append("| Engine | rules and templates, LLM off, shipped snapshot (no registration numbers) |\n")
                .append("| Server | Spring Boot over HTTP on localhost, Java ").append(Runtime.version().feature())
                .append(", ").append(System.getProperty("os.name")).append(", ")
                .append(Runtime.getRuntime().availableProcessors()).append(" CPUs |\n\n");
    }

    private boolean detection(StringBuilder md, List<Fixture> fixtures, Map<String, JsonNode> server) {
        List<Fixture> scams = ofKind(fixtures, "scam");
        List<Fixture> education = ofKind(fixtures, "education");
        List<Fixture> missed = scams.stream().filter(f -> !FLAGGED.contains(band(server, f))).toList();
        List<Fixture> flagged = education.stream().filter(f -> FLAGGED.contains(band(server, f))).toList();
        List<Fixture> classBreaks = education.stream().filter(f -> band(server, f).equals("high_concern")
                || !(contentClass(server, f).equals("education") || band(server, f).equals("few_flags_still_verify")))
                .toList();
        double detected = ratio(scams.size() - missed.size(), scams.size());
        double falseAlarms = ratio(flagged.size(), education.size());

        md.append("## Detection\n\n")
                .append("| Measure | Result | Target | |\n| --- | --- | --- | --- |\n")
                .append("| Scams at `some_concern` or higher | ").append(count(scams.size() - missed.size(), scams.size()))
                .append(" | ≥ 90% | ").append(met(detected >= DETECTION_TARGET)).append(" |\n")
                .append("| Education flagged (`some_concern` or higher) | ").append(count(flagged.size(), education.size()))
                .append(" | ≤ 10% | ").append(met(falseAlarms <= EDUCATION_FLAGGED_TARGET)).append(" |\n")
                .append("| Education read as `education` or `few_flags_still_verify`, never `high_concern` | ")
                .append(count(education.size() - classBreaks.size(), education.size())).append(" | 100% | ")
                .append(met(classBreaks.isEmpty())).append(" |\n");
        long exact = fixtures.stream().filter(f -> agrees(f, server.get(f.id()))).count();
        md.append("| Band, class and signal ids exactly as labelled | ").append(count((int) exact, fixtures.size()))
                .append(" | | |\n\n");

        listing(md, "Scams not flagged", missed, server);
        listing(md, "Education flagged", flagged, server);
        listing(md, "Education class rule broken", classBreaks, server);

        md.append("### Band by kind\n\n| Kind | high_concern | some_concern | few_flags_still_verify | not_enough_to_judge |\n")
                .append("| --- | --- | --- | --- | --- |\n");
        for (String kind : List.of("scam", "education", "ambiguous")) {
            md.append("| ").append(kind);
            for (String band : List.of("high_concern", "some_concern", "few_flags_still_verify", "not_enough_to_judge")) {
                md.append(" | ").append(ofKind(fixtures, kind).stream().filter(f -> band(server, f).equals(band)).count());
            }
            md.append(" |\n");
        }

        md.append("\n### Known gaps\n\nWhere the rules disagree with the labels. Each is pinned by `SignalFixtureTest`, ")
                .append("which fails once the rules are fixed so the note can be removed.\n\n")
                .append("| Fixture | Labelled | Engine | Why |\n| --- | --- | --- | --- |\n");
        for (Fixture fixture : fixtures) {
            if (fixture.hasKnownGap()) {
                JsonNode got = server.get(fixture.id());
                md.append("| ").append(fixture.id()).append(" | ").append(fixture.expectedBand()).append(", ")
                        .append(fixture.expectedClass()).append(", ").append(wanted(fixture)).append(" | ")
                        .append(got.path("band").asText()).append(", ").append(got.path("content_class").asText())
                        .append(", ").append(ids(got)).append(" | ").append(fixture.knownGap()).append(" |\n");
            }
        }

        md.append("\n### Per signal (all fixtures, LLM-only tags excluded)\n\n")
                .append("| Signal | Precision | Recall | TP | FP | FN |\n| --- | --- | --- | --- | --- | --- |\n");
        Map<String, int[]> scores = new TreeMap<>();
        for (Fixture fixture : fixtures) {
            Set<String> got = ids(server.get(fixture.id()));
            Set<String> want = wanted(fixture);
            got.forEach(id -> scores.computeIfAbsent(id, k -> new int[3])[want.contains(id) ? 0 : 1]++);
            want.stream().filter(id -> !got.contains(id)).forEach(id -> scores.computeIfAbsent(id, k -> new int[3])[2]++);
        }
        scores.forEach((id, s) -> md.append(String.format("| %s | %.2f | %.2f | %d | %d | %d |%n", id,
                s[0] + s[1] == 0 ? 1.0 : (double) s[0] / (s[0] + s[1]),
                s[0] + s[2] == 0 ? 1.0 : (double) s[0] / (s[0] + s[2]), s[0], s[1], s[2])));
        md.append('\n');
        return detected >= DETECTION_TARGET && falseAlarms <= EDUCATION_FLAGGED_TARGET && classBreaks.isEmpty();
    }

    private record Parity(boolean ran, int headline, int full) {
    }

    private Parity parity(StringBuilder md, List<Fixture> fixtures, Map<String, JsonNode> server) {
        md.append("## Server and on-device agreement\n\n");
        Map<String, JsonNode> device;
        try {
            device = onDevice();
        } catch (IOException | InterruptedException | IllegalStateException e) {
            md.append("Not run: ").append(e.getMessage())
                    .append(". Needs Node 24 on the PATH (or `-Druko.node=<path>`).\n\n");
            return new Parity(false, 0, 0);
        }
        int headline = 0;
        int full = 0;
        List<String> differ = new ArrayList<>();
        for (Fixture fixture : fixtures) {
            JsonNode s = server.get(fixture.id());
            JsonNode d = device.get(fixture.id());
            if (d == null) {
                differ.add(fixture.id() + " (no on-device result)");
                continue;
            }
            if (s.path("band").equals(d.path("band")) && s.path("content_class").equals(d.path("content_class"))
                    && ids(s).equals(ids(d))) {
                headline++;
            }
            if (withoutEngine(s).equals(withoutEngine(d))) {
                full++;
            } else {
                differ.add(fixture.id());
            }
        }
        md.append("The on-device engine (`pwa/src/engine`, run in Node on the client-masked text) against the live ")
                .append("server response.\n\n| Measure | Result | Target | |\n| --- | --- | --- | --- |\n")
                .append("| Same band, class and signal ids | ").append(count(headline, fixtures.size()))
                .append(" | 100% | ").append(met(headline == fixtures.size())).append(" |\n")
                .append("| Same full response (evidence, cards, entities), `engine` aside | ")
                .append(count(full, fixtures.size())).append(" | 100% | ").append(met(full == fixtures.size()))
                .append(" |\n\n");
        if (!differ.isEmpty()) {
            md.append("Differ: ").append(String.join(", ", differ)).append("\n\n");
        }
        return new Parity(true, headline, full);
    }

    private record Lint(int passed, int total, long served) {
    }

    private Lint linter(StringBuilder md, List<Fixture> fixtures) {
        int passed = 0;
        int total = 0;
        Map<String, Integer> codes = new TreeMap<>();
        for (Fixture fixture : fixtures) {
            for (Lang lang : Lang.values()) {
                for (SebiSnapshotIndex snapshot : List.of(Pipeline.shippedSnapshot(), Pipeline.datedSnapshot())) {
                    Pipeline.Drafted drafted = Pipeline.drafted(
                            new AnalyzeRequest(fixture.text(), lang, fixture.source(), fixture.ocrConfidence()), snapshot);
                    LintResult result = Pipeline.LINTER.lint(drafted.response(), drafted.context());
                    total++;
                    if (result.violations().isEmpty()) {
                        passed++;
                    }
                    result.codes().forEach(code -> codes.merge(code.name(), 1, Integer::sum));
                }
            }
        }
        double served = registry.find("ruko.lint.fail").counters().stream().mapToDouble(Counter::count).sum();
        md.append("## Guardrail linter\n\n| Measure | Result | Target | |\n| --- | --- | --- | --- |\n")
                .append("| Template outputs with zero violations (each fixture × hi/en × shipped/dated snapshot) | ")
                .append(count(passed, total)).append(" | 100% | ").append(met(passed == total)).append(" |\n")
                .append("| `ruko_lint_fail_total` after serving every fixture | ").append((long) served)
                .append(" | 0 | ").append(met(served == 0)).append(" |\n\n");
        if (!codes.isEmpty()) {
            md.append("Violations by code: ").append(codes).append("\n\n");
        }
        return new Lint(passed, total, (long) served);
    }

    private long[] latency(StringBuilder md, URI base, List<Fixture> fixtures) throws Exception {
        List<String> bodies = new ArrayList<>();
        for (Fixture fixture : fixtures) {
            bodies.add(body(fixture, ""));
        }
        for (int i = 0; i < WARMUP; i++) {
            post(base, bodies.get(i % bodies.size()));
        }
        ExecutorService pool = Executors.newFixedThreadPool(LOAD_THREADS);
        List<Future<Long>> futures = new ArrayList<>();
        long started = System.nanoTime();
        for (int i = 0; i < LOAD_REQUESTS; i++) {
            String body = bodies.get(i % bodies.size());
            futures.add(pool.submit(() -> {
                long t0 = System.nanoTime();
                HttpResponse<String> response = post(base, body);
                long took = System.nanoTime() - t0;
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("status " + response.statusCode());
                }
                return took;
            }));
        }
        List<Long> nanos = new ArrayList<>();
        for (Future<Long> future : futures) {
            nanos.add(future.get());
        }
        double seconds = (System.nanoTime() - started) / 1e9;
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        Collections.sort(nanos);
        long p50 = ms(percentile(nanos, 0.50));
        long p95 = ms(percentile(nanos, 0.95));
        long p99 = ms(percentile(nanos, 0.99));
        long max = ms(nanos.getLast());
        md.append("## Server latency\n\n")
                .append(String.format("`POST /api/v1/analyze`, LLM off, %d requests over the fixtures from %d concurrent ",
                        LOAD_REQUESTS, LOAD_THREADS))
                .append(String.format("clients after %d warm-up requests (%.0f requests/s). Measured on the same machine, ", WARMUP,
                        LOAD_REQUESTS / seconds))
                .append("so it leaves out the network.\n\n| p50 | p95 | p99 | max | Target p95 | |\n")
                .append("| --- | --- | --- | --- | --- | --- |\n")
                .append(String.format("| %d ms | %d ms | %d ms | %d ms | ≤ 1500 ms | %s |%n%n", p50, p95, p99, max,
                        met(p95 <= P95_TARGET_MS)));
        return new long[] {p50, p95};
    }

    private int payload(StringBuilder md, List<Integer> payloads) {
        List<Integer> sorted = payloads.stream().sorted().toList();
        int max = sorted.getLast();
        md.append("## Result payload\n\nThe `/analyze` response body, gzipped, per fixture.\n\n")
                .append("| Median | Largest | Target | |\n| --- | --- | --- | --- |\n")
                .append(String.format("| %.1f KB | %.1f KB | ≤ 6 KB | %s |%n%n", sorted.get(sorted.size() / 2) / 1024.0,
                        max / 1024.0, met(max <= MAX_GZIPPED_BYTES)));
        return max;
    }

    private int logAudit(StringBuilder md, URI base, List<Fixture> fixtures, CapturedOutput output) throws Exception {
        for (Fixture fixture : fixtures) {
            client.send(Canary.analyze(base, body(fixture, " " + Canary.VALUE)), BodyHandlers.discarding());
        }
        for (Canary.Case testCase : Canary.cases()) {
            Canary.send(base, testCase);
        }
        Canary.sendInvalidRequestTarget(port);
        String logs = output.getAll();
        int leaks = 0;
        for (int at = logs.indexOf(Canary.VALUE); at >= 0; at = logs.indexOf(Canary.VALUE, at + 1)) {
            leaks++;
        }
        long lines = logs.lines().count();
        md.append("## Log audit\n\n")
                .append(String.format("Every fixture again with a canary appended to the text and sent in a header, plus "
                        + "%d error-path requests carrying the canary in the path, query, headers, and body. ", Canary.cases().size() + 1))
                .append("Everything the server logged during the whole run was searched for it.\n\n")
                .append("| Log lines | Canary found | Target | |\n| --- | --- | --- | --- |\n")
                .append(String.format("| %d | %d | 0 | %s |%n%n", lines, leaks, met(leaks == 0)));
        return leaks;
    }

    private static void notMeasuredHere(StringBuilder md) {
        md.append("## Measured elsewhere\n\n")
                .append("- Text path on emulated slow 3G with a 4× slower CPU: `cd pwa && npm run build && npm run test:perf`, ")
                .append("which writes [eval-text-path.md](./eval-text-path.md). A real low-end Android phone is still needed for the final number.\n")
                .append("- Shell bundle size: `cd pwa && npm run check:size` (budget 200 KB gzipped; a CI gate).\n")
                .append("- Screen reader: `pwa/e2e/result-a11y.test.js` in CI.\n")
                .append("- Behaviour study: [protocol and sheets](./study/README.md); results come from real sessions only.\n\n");
    }

    private static void summary(StringBuilder md, boolean detectionMet, Parity parity, int fixtures, Lint lint,
                                long[] latency, int maxPayload, int leaks) {
        StringBuilder top = new StringBuilder("## Summary\n\n| Target | |\n| --- | --- |\n")
                .append("| Detection and education targets | ").append(met(detectionMet)).append(" |\n")
                .append("| Server and on-device agreement 100% | ")
                .append(parity.ran() ? met(parity.headline() == fixtures && parity.full() == fixtures) : "not run")
                .append(" |\n")
                .append("| Linter pass rate 100% | ").append(met(lint.passed() == lint.total() && lint.served() == 0))
                .append(" |\n")
                .append("| Server p95 ≤ 1.5 s (LLM off) | ").append(met(latency[1] <= P95_TARGET_MS)).append(" |\n")
                .append("| Result ≤ 6 KB gzipped | ").append(met(maxPayload <= MAX_GZIPPED_BYTES)).append(" |\n")
                .append("| Zero content in logs | ").append(met(leaks == 0)).append(" |\n\n");
        int afterHeader = md.indexOf("## Detection");
        md.insert(afterHeader, top);
    }

    private static void listing(StringBuilder md, String title, List<Fixture> fixtures, Map<String, JsonNode> server) {
        if (fixtures.isEmpty()) {
            return;
        }
        md.append("**").append(title).append(":** ");
        md.append(fixtures.stream().map(f -> f.id() + " (" + band(server, f) + ", " + contentClass(server, f) + ")")
                .collect(Collectors.joining(", "))).append("\n\n");
    }

    private Map<String, JsonNode> onDevice() throws IOException, InterruptedException {
        Process process = new ProcessBuilder(System.getProperty("ruko.node", "node"), "scripts/on-device-results.mjs")
                .directory(PWA.toFile()).redirectError(Redirect.DISCARD).start();
        byte[] out;
        try (InputStream in = process.getInputStream()) {
            out = in.readAllBytes();
        }
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("node scripts/on-device-results.mjs failed");
        }
        Map<String, JsonNode> results = new LinkedHashMap<>();
        for (String line : new String(out, StandardCharsets.UTF_8).split("\n")) {
            if (!line.isBlank()) {
                JsonNode entry = JSON.readTree(line);
                results.put(entry.path("id").asText(), entry.path("response"));
            }
        }
        return results;
    }

    private HttpResponse<String> post(URI base, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(base.resolve(Canary.ANALYZE))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String body(Fixture fixture, String suffix) throws IOException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("text", fixture.text() + suffix);
        request.put("lang", fixture.lang().wire());
        request.put("source", fixture.source().wire());
        if (fixture.ocrConfidence() != null) {
            request.put("ocr_confidence", fixture.ocrConfidence());
        }
        return JSON.writeValueAsString(request);
    }

    private static boolean agrees(Fixture fixture, JsonNode response) {
        return response.path("band").asText().equals(fixture.expectedBand())
                && response.path("content_class").asText().equals(fixture.expectedClass())
                && ids(response).equals(wanted(fixture));
    }

    private static Set<String> wanted(Fixture fixture) {
        return fixture.expectedSignals().stream().filter(id -> !LLM_ONLY.contains(id))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> ids(JsonNode response) {
        Set<String> ids = new TreeSet<>();
        for (String field : List.of("signals", "unverified", "reassuring")) {
            StreamSupport.stream(response.path(field).spliterator(), false).forEach(s -> ids.add(s.path("id").asText()));
        }
        return ids;
    }

    private static JsonNode withoutEngine(JsonNode response) {
        ObjectNode copy = response.deepCopy();
        copy.remove("engine");
        return copy;
    }

    private static List<Fixture> ofKind(List<Fixture> fixtures, String kind) {
        return fixtures.stream().filter(f -> f.kind().equals(kind)).toList();
    }

    private static String band(Map<String, JsonNode> server, Fixture fixture) {
        return server.get(fixture.id()).path("band").asText();
    }

    private static String contentClass(Map<String, JsonNode> server, Fixture fixture) {
        return server.get(fixture.id()).path("content_class").asText();
    }

    private static int gzipped(String body) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return out.size();
    }

    private static long percentile(List<Long> sorted, double p) {
        return sorted.get(Math.max(0, (int) Math.ceil(p * sorted.size()) - 1));
    }

    private static long ms(long nanos) {
        return Math.round(nanos / 1e6);
    }

    private static double ratio(int a, int b) {
        return b == 0 ? 0 : (double) a / b;
    }

    private static String count(int a, int b) {
        return String.format("%d / %d (%.1f%%)", a, b, 100 * ratio(a, b));
    }

    private static String met(boolean met) {
        return met ? "met" : "**not met**";
    }
}
