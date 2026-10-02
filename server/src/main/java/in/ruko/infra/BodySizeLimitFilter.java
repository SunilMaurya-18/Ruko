package in.ruko.infra;

import static in.ruko.infra.SafeLog.num;
import static in.ruko.infra.SafeLog.tag;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ruko.api.error.ProblemType;
import in.ruko.infra.config.AnalyzeProps;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects API bodies above {@code max-body-bytes}, or of undeclared length, before anything reads them. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class BodySizeLimitFilter extends OncePerRequestFilter {

    private static final SafeLog LOG = SafeLog.of(BodySizeLimitFilter.class);
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    private final long maxBytes;
    private final ObjectMapper objectMapper;

    public BodySizeLimitFilter(AnalyzeProps props, ObjectMapper objectMapper) {
        this.maxBytes = props.maxBodyBytes();
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || !BODY_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        ProblemType problem = length < 0 ? ProblemType.LENGTH_REQUIRED
                : length > maxBytes ? ProblemType.PAYLOAD_TOO_LARGE : null;
        if (problem == null) {
            chain.doFilter(request, response);
            return;
        }
        LOG.warn(LogEvent.REQUEST_REJECTED, num(LogKey.STATUS, problem.status().value()), tag(LogKey.PROBLEM, problem));
        response.setStatus(problem.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem.toProblemDetail());
    }
}
