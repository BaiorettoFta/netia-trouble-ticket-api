package pl.netia.troubleticket.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import pl.netia.troubleticket.support.IntegrationTest;
import pl.netia.troubleticket.support.TestJwtIssuer;
import pl.netia.troubleticket.support.TestJwtIssuer.Token;

/**
 * Real signed tokens verified through JWKS: what "a correctly verified token" means for this API.
 */
class AuthenticationIT extends IntegrationTest {

    static Stream<Arguments> invalidTokens() {
        Instant now = Instant.now();
        return Stream.of(
                invalid("expired beyond the clock skew", token -> token.claim("exp", now.minusSeconds(120))),
                invalid("not valid yet", token -> token.claim("nbf", now.plusSeconds(120))),
                invalid("without expiry", token -> token.without("exp")),
                invalid("from another issuer", token -> token.claim("iss", "https://idp.example.com/other")),
                invalid("for another audience", token -> token.claim("aud", "billing-api")),
                invalid("signed with an untrusted key", Token::signedWithUntrustedKey),
                invalid("not signed (alg none)", Token::unsigned),
                invalid("without tenant", token -> token.without("tenant_id")),
                invalid("with malformed tenant", token -> token.tenant("tenant a; drop")),
                invalid("without subject", token -> token.without("sub")));
    }

    @ParameterizedTest(name = "token {0}")
    @MethodSource("invalidTokens")
    void rejectsTokenThatIsNotCorrectlyVerified(String bearer) {
        client.get().uri("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().value(HttpHeaders.WWW_AUTHENTICATE,
                        value -> assertThat(value).contains("error=\"invalid_token\""))
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void rejectsMissingToken() {
        client.get().uri("/api/v1/troubleTicket")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").isEqualTo("Brak poprawnego Bearer tokenu.");
    }

    @Test
    void acceptsCorrectlyVerifiedToken() {
        unvalidatedClient.get().uri("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotIn(401, 403));
    }

    private static Arguments invalid(String description, UnaryOperator<Token> change) {
        return Arguments.of(Named.of(description, change.apply(TestJwtIssuer.token()).asBearer()));
    }
}
