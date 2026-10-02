package in.ruko.infra;

import static in.ruko.infra.SafeLog.num;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ruko.api.error.ProblemType;
import in.ruko.infra.config.RateLimitProps;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed one-minute window per client IP over {@code /api/**}. In memory only; IPs are never logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final SafeLog LOG = SafeLog.of(RateLimitFilter.class);
    private static final long WINDOW_MS = 60_000;
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final int limit;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitFilter(RateLimitProps props, ObjectMapper objectMapper) {
        this(props, objectMapper, Clock.systemUTC());
    }

    RateLimitFilter(RateLimitProps props, ObjectMapper objectMapper, Clock clock) {
        this.limit = props.perIpPerMin();
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !RequestPaths.of(request).startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        long windowStart = now - now % WINDOW_MS;
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(window -> window.start < windowStart);
        }

        Window window = windows.compute(request.getRemoteAddr(),
                (ip, current) -> current == null || current.start != windowStart ? new Window(windowStart) : current);

        if (window.count.incrementAndGet() > limit) {
            long retryAfterSeconds = Math.max(1, (windowStart + WINDOW_MS - now + 999) / 1000);
            LOG.warn(LogEvent.RATE_LIMITED, num(LogKey.STATUS, 429), num(LogKey.LIMIT, limit));
            ProblemType problem = ProblemType.TOO_MANY_REQUESTS;
            response.setStatus(problem.status().value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
            objectMapper.writeValue(response.getOutputStream(), problem.toProblemDetail());
            return;
        }
        chain.doFilter(request, response);
    }

    private static final class Window {
        private final long start;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long start) {
            this.start = start;
        }
    }
}
