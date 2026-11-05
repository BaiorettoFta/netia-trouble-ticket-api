package pl.netia.troubleticket.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import pl.netia.troubleticket.web.ErrorCode;
import pl.netia.troubleticket.web.ErrorResponseWriter;

/**
 * Answers a missing or invalid token with 401: the standard entry point sets the {@code WWW-Authenticate} header
 * required by RFC 6750, and the body follows the contract {@code Error} schema.
 */
final class ErrorBodyAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final ErrorResponseWriter errorResponseWriter;

    ErrorBodyAuthenticationEntryPoint(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        bearerEntryPoint.commence(request, response, exception);
        errorResponseWriter.write(request, response, ErrorCode.UNAUTHORIZED);
    }
}
