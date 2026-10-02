package in.ruko.support;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;

/** A single-file multipart/form-data POST, with an optional consent header. */
public final class Multipart {

    private static final String BOUNDARY = "ruko-test-boundary-5d1c";

    private Multipart() {
    }

    public static HttpRequest audio(URI uri, byte[] audio, String consent) {
        return audio(uri, audio, consent, "note.opus");
    }

    public static HttpRequest audio(URI uri, byte[] audio, String consent, String filename) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"audio\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: audio/ogg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(audio);
        body.writeBytes(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        if (consent != null) {
            request.header("X-Consent", consent);
        }
        return request.build();
    }
}
