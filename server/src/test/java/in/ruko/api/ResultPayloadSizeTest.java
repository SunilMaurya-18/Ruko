package in.ruko.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

/**
 * Result payload budget (TRD: at most 6 KB gzipped) over every fixture and edge-case probe. Reads
 * {@code engine-golden.v0.json}, which {@code OnDeviceParityTest} keeps equal to the server's responses.
 */
class ResultPayloadSizeTest {

    static final int MAX_GZIPPED_BYTES = 6 * 1024;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void everyResultFitsTheBudget() throws IOException {
        JsonNode golden;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("fixtures/engine-golden.v0.json")) {
            golden = JSON.readTree(in);
        }
        assertThat(golden.size()).isGreaterThanOrEqualTo(80);
        for (JsonNode entry : golden) {
            int size = gzipped(JSON.writeValueAsString(entry.path("response")));
            assertThat(size).as(entry.path("id").asText()).isLessThanOrEqualTo(MAX_GZIPPED_BYTES);
        }
    }

    static int gzipped(String body) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return out.size();
    }
}
