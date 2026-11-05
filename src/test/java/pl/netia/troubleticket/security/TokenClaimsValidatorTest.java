package pl.netia.troubleticket.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class TokenClaimsValidatorTest {

    private final TokenClaimsValidator validator = new TokenClaimsValidator();

    @Test
    void acceptsTokenWithExpirySubjectAndTenant() {
        OAuth2TokenValidatorResult result = validator.validate(token().build());

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenWithoutExpiry() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256")
                .subject("partner-a").claim("tenant_id", "tenant-a").build();

        assertThat(errorDescriptions(validator.validate(jwt))).containsExactly(
                "The token must have an expiration time (exp)");
    }

    @Test
    void rejectsTokenWithoutSubject() {
        Jwt jwt = token().claims(claims -> claims.remove("sub")).build();

        assertThat(errorDescriptions(validator.validate(jwt))).containsExactly(
                "The token must identify its subject (sub)");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "tenant a", "tenant/a", "tenant-a\n",
            "01234567890123456789012345678901234567890123456789012345678901234"})
    void rejectsMissingOrMalformedTenant(String tenant) {
        Jwt jwt = token().claims(claims -> {
            if (tenant == null) {
                claims.remove("tenant_id");
            } else {
                claims.put("tenant_id", tenant);
            }
        }).build();

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getErrorCode()).isEqualTo("invalid_token");
            assertThat(error.getDescription()).contains("tenant_id");
        });
    }

    @Test
    void reportsEveryMissingClaimAtOnce() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").claim("scope", "trouble-ticket:read").build();

        assertThat(validator.validate(jwt).getErrors()).hasSize(3);
    }

    private static Jwt.Builder token() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("partner-a")
                .claim("tenant_id", "tenant-a")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }

    private static Iterable<String> errorDescriptions(OAuth2TokenValidatorResult result) {
        return result.getErrors().stream().map(OAuth2Error::getDescription).toList();
    }
}
