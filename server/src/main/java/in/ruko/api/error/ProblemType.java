package in.ruko.api.error;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Fixed RFC 9457 problem catalogue. Titles and details are constants so an error response can never quote
 * the request that caused it.
 */
public enum ProblemType {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "bad-request", "Bad request",
            "The request could not be processed."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "not-found", "Not found",
            "The requested resource does not exist."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Method not allowed",
            "This method is not supported for the requested resource."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "not-acceptable", "Not acceptable",
            "The requested response format is not available."),
    LENGTH_REQUIRED(HttpStatus.LENGTH_REQUIRED, "length-required", "Length required",
            "The request must declare its body length."),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "payload-too-large", "Payload too large",
            "The request body is larger than allowed."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type", "Unsupported media type",
            "The request body format is not supported."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests", "Too many requests",
            "Too many requests. Please wait a minute and try again."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error",
            "Something went wrong. Please try again."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "service-unavailable", "Service unavailable",
            "The service is temporarily unavailable.");

    private final HttpStatus status;
    private final URI type;
    private final String title;
    private final String detail;

    ProblemType(HttpStatus status, String slug, String title, String detail) {
        this.status = status;
        this.type = URI.create("urn:ruko:problem:" + slug);
        this.title = title;
        this.detail = detail;
    }

    public static ProblemType forStatus(HttpStatusCode status) {
        for (ProblemType problem : values()) {
            if (problem.status.value() == status.value()) {
                return problem;
            }
        }
        return status.is4xxClientError() ? BAD_REQUEST : INTERNAL_ERROR;
    }

    public HttpStatus status() {
        return status;
    }

    public ProblemDetail toProblemDetail() {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(type);
        problem.setTitle(title);
        problem.setDetail(detail);
        // Spring fills a missing instance with the request path, which would echo the request.
        problem.setInstance(URI.create("urn:uuid:" + UUID.randomUUID()));
        return problem;
    }

    public ResponseEntity<Object> toResponse() {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(toProblemDetail());
    }
}
