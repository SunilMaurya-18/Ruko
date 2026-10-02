package in.ruko.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ruko.infra.config.RateLimitProps;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private final AtomicLong now = new AtomicLong(Instant.parse("2026-10-02T09:00:10Z").toEpochMilli());
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final RateLimitFilter filter = new RateLimitFilter(new RateLimitProps(2), objectMapper, new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(now.get());
        }

        @Override
        public long millis() {
            return now.get();
        }
    });

    @Test
    void allowsUpToLimitThenReturnsProblem429WithRetryAfter() throws Exception {
        assertThat(call("10.0.0.1", "/api/v1/analyze").getStatus()).isEqualTo(200);
        assertThat(call("10.0.0.1", "/api/v1/analyze").getStatus()).isEqualTo(200);

        MockHttpServletResponse limited = call("10.0.0.1", "/api/v1/analyze");

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getContentType()).startsWith("application/problem+json");
        assertThat(limited.getHeader("Retry-After")).isEqualTo("50");
        assertThat(objectMapper.readTree(limited.getContentAsString()).path("type").asText())
                .isEqualTo("urn:ruko:problem:too-many-requests");
    }

    @Test
    void countsEachClientSeparately() throws Exception {
        call("10.0.0.1", "/api/v1/analyze");
        call("10.0.0.1", "/api/v1/analyze");

        assertThat(call("10.0.0.2", "/api/v1/analyze").getStatus()).isEqualTo(200);
    }

    @Test
    void resetsInTheNextMinute() throws Exception {
        call("10.0.0.1", "/api/v1/analyze");
        call("10.0.0.1", "/api/v1/analyze");
        now.addAndGet(60_000);

        assertThat(call("10.0.0.1", "/api/v1/analyze").getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotLimitNonApiPaths() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(call("10.0.0.1", "/actuator/health").getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse call(String ip, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
