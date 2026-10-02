package in.ruko.infra;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The path filters should match on. The raw request URI keeps {@code ;params} and encodings that Spring's handler
 * mapping ignores, so {@code /api;x/v1/analyze} would reach a controller while missing a {@code startsWith("/api/")}
 * check. The servlet path is the container's decoded, normalised path without path parameters.
 */
final class RequestPaths {

    private RequestPaths() {
    }

    static String of(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath == null || servletPath.isEmpty()) {
            return request.getRequestURI();
        }
        return request.getPathInfo() == null ? servletPath : servletPath + request.getPathInfo();
    }
}
