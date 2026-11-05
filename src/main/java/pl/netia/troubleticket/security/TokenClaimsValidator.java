package pl.netia.troubleticket.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import pl.netia.troubleticket.ticket.domain.TenantId;

/**
 * Claims this API needs on top of the standard checks (signature, issuer, audience, time window) that Spring Boot
 * configures. A token without a tenant cannot be used here at all, so it is rejected as {@code invalid_token}.
 * The expiry is required because the standard timestamp check accepts a token that has no {@code exp} claim.
 */
final class TokenClaimsValidator implements OAuth2TokenValidator<Jwt> {

    static final String TENANT_CLAIM = "tenant_id";

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<OAuth2Error> errors = new ArrayList<>();
        if (jwt.getExpiresAt() == null) {
            errors.add(invalidToken("The token must have an expiration time (exp)"));
        }
        if (!StringUtils.hasText(jwt.getSubject())) {
            errors.add(invalidToken("The token must identify its subject (sub)"));
        }
        if (!TenantId.isValid(jwt.getClaimAsString(TENANT_CLAIM))) {
            errors.add(invalidToken("The token must carry a valid tenant scope (" + TENANT_CLAIM + ")"));
        }
        return errors.isEmpty() ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(errors);
    }

    private static OAuth2Error invalidToken(String description) {
        return new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null);
    }
}
