package in.ruko.infra;

import static in.ruko.infra.SafeLog.num;
import static in.ruko.infra.SafeLog.tag;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ruko.api.error.ProblemType;
import in.ruko.infra.config.FeatureFlags;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gate in front of {@code /api/v1/voice/asr}, before any upload is read: the route does not exist unless
 * {@code ruko.features.asr} is on, and audio is only accepted with {@code X-Consent: voice-asr-v1}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class AsrGateFilter extends OncePerRequestFilter {

    public static final String PATH = "/api/v1/voice/asr";
    public static final String CONSENT_HEADER = "X-Consent";
    public static final String CONSENT = "voice-asr-v1";

    private static final SafeLog LOG = SafeLog.of(AsrGateFilter.class);

    private enum Reason { FEATURE_OFF, CONSENT_REQUIRED }

    private final FeatureFlags flags;
    private final ObjectMapper objectMapper;

    public AsrGateFilter(FeatureFlags flags, ObjectMapper objectMapper) {
        this.flags = flags;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PATH.equals(RequestPaths.of(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!flags.asr()) {
            reject(response, ProblemType.NOT_FOUND, Reason.FEATURE_OFF);
        } else if (!CONSENT.equals(request.getHeader(CONSENT_HEADER))) {
            reject(response, ProblemType.BAD_REQUEST, Reason.CONSENT_REQUIRED);
        } else {
            chain.doFilter(request, response);
        }
    }

    private void reject(HttpServletResponse response, ProblemType problem, Reason reason) throws IOException {
        LOG.warn(LogEvent.REQUEST_REJECTED, num(LogKey.STATUS, problem.status().value()), tag(LogKey.PROBLEM, problem),
                tag(LogKey.REASON, reason));
        ProblemDetail body = problem.toProblemDetail();
        if (reason == Reason.CONSENT_REQUIRED) {
            body.setProperty("reason", "consent_required");
        }
        response.setStatus(problem.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
