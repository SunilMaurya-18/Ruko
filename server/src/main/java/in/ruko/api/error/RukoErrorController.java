package in.ruko.api.error;

import static in.ruko.infra.SafeLog.errorType;
import static in.ruko.infra.SafeLog.num;
import static in.ruko.infra.SafeLog.tag;

import in.ruko.infra.LogEvent;
import in.ruko.infra.LogKey;
import in.ruko.infra.SafeLog;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Replaces Boot's default error body, which includes the request path. */
@RestController
public class RukoErrorController implements ErrorController {

    private static final SafeLog LOG = SafeLog.of(RukoErrorController.class);

    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<Object> error(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = code instanceof Integer value ? value : 500;
        ProblemType problem = ProblemType.forStatus(HttpStatusCode.valueOf(status));

        if (request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable error) {
            LOG.error(LogEvent.UNHANDLED_ERROR, num(LogKey.STATUS, status), tag(LogKey.PROBLEM, problem),
                    errorType(error));
        } else {
            LOG.warn(LogEvent.REQUEST_REJECTED, num(LogKey.STATUS, status), tag(LogKey.PROBLEM, problem));
        }
        return problem.toResponse();
    }
}
