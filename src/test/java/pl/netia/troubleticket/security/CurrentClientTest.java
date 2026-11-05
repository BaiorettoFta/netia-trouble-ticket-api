package pl.netia.troubleticket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import pl.netia.troubleticket.ticket.domain.TenantId;

class CurrentClientTest {

    private final CurrentClient currentClient = new CurrentClient();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsTenantAndSubjectFromVerifiedToken() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256")
                .subject("partner-a").claim("tenant_id", "tenant-a")
                .issuedAt(now).expiresAt(now.plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(currentClient.get()).isEqualTo(new AuthenticatedClient(new TenantId("tenant-a"), "partner-a"));
    }

    @Test
    void failsLoudlyWhenRequestIsNotAuthenticatedWithJwt() {
        assertThatIllegalStateException().isThrownBy(currentClient::get);
    }
}
