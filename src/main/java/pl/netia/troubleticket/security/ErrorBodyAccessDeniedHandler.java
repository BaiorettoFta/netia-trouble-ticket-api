package pl.netia.troubleticket.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import pl.netia.troubleticket.web.ErrorCode;
import pl.netia.troubleticket.web.ErrorResponseWriter;

/**
 * Answers a valid token without the required scope with 403: the standard handler sets
 * {@code WWW-Authenticate: Bearer error="insufficient_scope"} (RFC 6750), and the body follows the contract.
 */
final class ErrorBodyAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorBodyAccessDeniedHandler.class);

    private final AccessDeniedHandler bearerAccessDeniedHandler = new BearerTokenAccessDeniedHandler();
    private final ErrorResponseWriter errorResponseWriter;

    ErrorBodyAccessDeniedHandler(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException, ServletException {
        log.info("Access denied for {} {}", request.getMethod(), request.getRequestURI());
        bearerAccessDeniedHandler.handle(request, response, exception);
        errorResponseWriter.write(request, response, ErrorCode.FORBIDDEN);
    }
}
