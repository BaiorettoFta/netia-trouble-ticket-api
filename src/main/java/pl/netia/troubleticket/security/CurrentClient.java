package pl.netia.troubleticket.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import pl.netia.troubleticket.ticket.domain.TenantId;

/**
 * Gives controllers the authenticated caller. The controller method signatures are generated from the contract,
 * so the caller cannot be passed as a method argument and is read from the security context instead.
 */
@Component
public class CurrentClient {

    public AuthenticatedClient get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            return new AuthenticatedClient(
                    new TenantId(jwt.getClaimAsString(TokenClaimsValidator.TENANT_CLAIM)), jwt.getSubject());
        }
        throw new IllegalStateException("The request is not authenticated with a verified JWT");
    }
}
