package pl.netia.troubleticket.support;

import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Stands in for the real decoder in web slice tests, where signatures and claim validation are not under test
 * (integration tests cover them with real signed tokens). A test token names its tenant and scopes, for example
 * {@code test~tenant-a~read+write}; anything else is rejected as an invalid token.
 */
public class FakeJwtDecoder implements JwtDecoder {

    private static final String PREFIX = "test~";

    public static String bearer(String tenant, String... scopes) {
        return "Bearer " + PREFIX + tenant + "~" + String.join("+", scopes);
    }

    @Override
    public Jwt decode(String token) {
        String[] parts = token.split("~", -1);
        if (!token.startsWith(PREFIX) || parts.length != 3) {
            throw new BadJwtException("Unrecognised test token");
        }
        String scope = Arrays.stream(parts[2].split("\\+"))
                .filter(name -> !name.isEmpty())
                .map(name -> "trouble-ticket:" + name)
                .collect(Collectors.joining(" "));
        Instant now = Instant.now();
        return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("test-client")
                .claim("tenant_id", parts[1])
                .claim("scope", scope)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
