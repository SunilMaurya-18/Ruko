package in.ruko.api.error;

import static in.ruko.infra.SafeLog.errorType;
import static in.ruko.infra.SafeLog.num;
import static in.ruko.infra.SafeLog.tag;

import in.ruko.infra.LogEvent;
import in.ruko.infra.LogKey;
import in.ruko.infra.SafeLog;
import in.ruko.pipeline.InputRejectedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final SafeLog LOG = SafeLog.of(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (request instanceof ServletWebRequest servlet
                && servlet.getResponse() != null && servlet.getResponse().isCommitted()) {
            return null;
        }
        ProblemType problem = ProblemType.forStatus(statusCode);
        LOG.warn(LogEvent.REQUEST_REJECTED, num(LogKey.STATUS, problem.status().value()),
                tag(LogKey.PROBLEM, problem), errorType(ex));
        return ResponseEntity.status(problem.status())
                .headers(serverGeneratedHeaders(headers))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem.toProblemDetail());
    }

    /** The default implementation logs the client-supplied method name through Spring's PageNotFound logger. */
    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, null, headers, status, request);
    }

    @ExceptionHandler(InputRejectedException.class)
    public ResponseEntity<Object> handleInputRejected(InputRejectedException ex) {
        ProblemType problem = ex.reason() == InputRejectedException.Reason.TOO_LONG
                ? ProblemType.PAYLOAD_TOO_LARGE : ProblemType.BAD_REQUEST;
        LOG.warn(LogEvent.REQUEST_REJECTED, num(LogKey.STATUS, problem.status().value()),
                tag(LogKey.PROBLEM, problem), tag(LogKey.REASON, ex.reason()));
        ProblemDetail body = problem.toProblemDetail();
        body.setProperty("reason", ex.reason().wire());
        return ResponseEntity.status(problem.status()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        LOG.error(LogEvent.UNHANDLED_ERROR, num(LogKey.STATUS, 500), errorType(ex));
        return ProblemType.INTERNAL_ERROR.toResponse();
    }

    private static HttpHeaders serverGeneratedHeaders(HttpHeaders source) {
        HttpHeaders safe = new HttpHeaders();
        if (source != null) {
            if (!source.getAllow().isEmpty()) {
                safe.setAllow(source.getAllow());
            }
            if (!source.getAccept().isEmpty()) {
                safe.setAccept(source.getAccept());
            }
        }
        return safe;
    }
}
