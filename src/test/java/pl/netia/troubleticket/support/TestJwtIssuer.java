package pl.netia.troubleticket.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A minimal identity provider for integration tests: an RSA key generated in memory, its public part published as a
 * JWKS document by the JDK's built-in HTTP server, and a builder for signed tokens. The application verifies these
 * tokens exactly as in production (JWKS, issuer, audience, time window, required claims); nothing is mocked.
 */
public final class TestJwtIssuer {

    public static final String ISSUER = "https://idp.test/realms/netia";
    public static final String AUDIENCE = "trouble-ticket-api";

    private static final String KEY_ID = "test-signing-key";
    private static final RSAKey SIGNING_KEY = generateKey();
    /** Different key material under the same key id: a forged signature. */
    private static final RSAKey UNTRUSTED_KEY = generateKey();

    private static HttpServer jwksServer;

    private TestJwtIssuer() {
    }

    public static synchronized String jwkSetUri() {
        if (jwksServer == null) {
            jwksServer = startJwksServer();
        }
        InetSocketAddress address = jwksServer.getAddress();
        return "http://" + address.getHostString() + ":" + address.getPort() + "/jwks";
    }

    public static Token token() {
        return new Token();
    }

    private static HttpServer startJwksServer() {
        byte[] jwks = new JWKSet(SIGNING_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(jwks);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * A valid access token by default; each method breaks or changes one property.
     */
    public static final class Token {

        private final Map<String, Object> claims = new LinkedHashMap<>();
        private RSAKey key = SIGNING_KEY;
        private boolean unsigned;

        private Token() {
            Instant now = Instant.now();
            claims.put("iss", ISSUER);
            claims.put("aud", AUDIENCE);
            claims.put("sub", "partner-a");
            claims.put("tenant_id", "tenant-a");
            claims.put("scope", "trouble-ticket:read trouble-ticket:write");
            claims.put("iat", now);
            claims.put("nbf", now);
            claims.put("exp", now.plusSeconds(300));
        }

        public Token tenant(String tenantId) {
            return claim("tenant_id", tenantId);
        }

        public Token subject(String subject) {
            return claim("sub", subject);
        }

        public Token scopes(String... scopes) {
            return claim("scope", String.join(" ", scopes));
        }

        public Token claim(String name, Object value) {
            claims.put(name, value);
            return this;
        }

        public Token without(String claim) {
            claims.remove(claim);
            return this;
        }

        public Token signedWithUntrustedKey() {
            key = UNTRUSTED_KEY;
            return this;
        }

        public Token unsigned() {
            unsigned = true;
            return this;
        }

        public String asBearer() {
            return "Bearer " + encode();
        }

        private String encode() {
            JWTClaimsSet.Builder claimsSet = new JWTClaimsSet.Builder();
            claims.forEach((name, value) ->
                    claimsSet.claim(name, value instanceof Instant instant ? Date.from(instant) : value));
            if (unsigned) {
                return new PlainJWT(claimsSet.build()).serialize();
            }
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(),
                    claimsSet.build());
            try {
                jwt.sign(new RSASSASigner(key));
            } catch (JOSEException exception) {
                throw new IllegalStateException(exception);
            }
            return jwt.serialize();
        }
    }
}
