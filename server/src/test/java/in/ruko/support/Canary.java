package in.ruko.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

/** Requests that carry a canary string in every place a client controls: path, query, headers, and body. */
public final class Canary {

    public static final String VALUE = "CANARY-7F3A9C";

    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    public record Case(String name, int expectedStatus, Function<URI, HttpRequest> request) {
        @Override
        public String toString() {
            return name;
        }
    }

    private Canary() {
    }

    public static List<Case> cases() {
        String oversized = VALUE + " " + "x".repeat(4100);
        return List.of(
                new Case("malformed JSON", 400, base -> json(base, "{\"text\": \"" + VALUE + "\", ")),
                new Case("wrong JSON type", 400, base -> json(base, "{\"text\": [\"" + VALUE + "\"], \"lang\": \"hi\"}")),
                new Case("oversized text", 400, base -> json(base, "{\"text\": \"" + oversized + "\", \"lang\": \"hi\"}")),
                new Case("invalid enum value", 400, base -> json(base, "{\"text\": \"hello\", \"lang\": \"" + VALUE + "\"}")),
                new Case("unsupported media type", 415, base -> HttpRequest.newBuilder(base.resolve(CanaryProbeController.PATH))
                        .header("Content-Type", "text/plain")
                        .POST(BodyPublishers.ofString(VALUE)).build()),
                new Case("unknown path", 404, base -> HttpRequest.newBuilder(base.resolve("/api/v1/" + VALUE + "?q=" + VALUE))
                        .header("X-Note", VALUE).GET().build()),
                new Case("method not allowed", 405, base -> HttpRequest.newBuilder(base.resolve(CanaryProbeController.PATH))
                        .header("Content-Type", "application/json")
                        .PUT(BodyPublishers.ofString("{\"text\": \"" + VALUE + "\"}")).build()),
                new Case("unknown method token", 405, base -> HttpRequest.newBuilder(base.resolve(CanaryProbeController.PATH))
                        .method(VALUE, BodyPublishers.noBody()).build()),
                new Case("unhandled exception", 500, base -> HttpRequest.newBuilder(
                                base.resolve(CanaryProbeController.PATH + "/boom?q=" + VALUE))
                        .header("User-Agent", VALUE).GET().build()),
                new Case("analyze: text over max-chars", 413, base -> analyze(base,
                        "{\"text\": \"" + oversized + "\", \"lang\": \"hi\", \"source\": \"paste\"}")),
                new Case("analyze: NUL in text", 400, base -> analyze(base,
                        "{\"text\": \"\\u0000" + VALUE + "\", \"lang\": \"hi\", \"source\": \"paste\"}")),
                new Case("analyze: unpaired surrogate", 400, base -> analyze(base,
                        "{\"text\": \"\\ud800 " + VALUE + "\", \"lang\": \"hi\", \"source\": \"paste\"}")),
                new Case("analyze: unknown source", 400, base -> analyze(base,
                        "{\"text\": \"hello there\", \"lang\": \"hi\", \"source\": \"" + VALUE + "\"}")),
                new Case("analyze: body over byte limit", 413, base -> analyze(base,
                        "{\"text\": \"" + VALUE + "y".repeat(70_000) + "\", \"lang\": \"hi\", \"source\": \"paste\"}")),
                new Case("analyze: chunked body without length", 411, base -> HttpRequest.newBuilder(base.resolve(ANALYZE))
                        .header("Content-Type", "application/json")
                        .POST(BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(
                                ("{\"text\": \"" + VALUE + "\"}").getBytes(StandardCharsets.UTF_8))))
                        .build()));
    }

    public static final String ANALYZE = "/api/v1/analyze";

    public static HttpRequest analyze(URI base, String body) {
        return HttpRequest.newBuilder(base.resolve(ANALYZE))
                .header("Content-Type", "application/json")
                .header("X-Note", VALUE)
                .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    public static HttpResponse<String> send(URI base, Case testCase) throws IOException, InterruptedException {
        return CLIENT.send(testCase.request().apply(base), BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** Sends a request line Tomcat rejects before Spring sees it. Returns the raw HTTP response. */
    public static String sendInvalidRequestTarget(int port) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET /" + VALUE + "{|} HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static HttpRequest json(URI base, String body) {
        return HttpRequest.newBuilder(base.resolve(CanaryProbeController.PATH))
                .header("Content-Type", "application/json")
                .header("X-Note", VALUE)
                .POST(BodyPublishers.ofString(body))
                .build();
    }
}
