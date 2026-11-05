package pl.netia.troubleticket.support;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The whole application on a random port with PostgreSQL in Testcontainers and real token verification.
 *
 * <p>Every test class gets its own random tenant, so tests are isolated without cleaning tables, and tenant
 * isolation itself is exercised all the time.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    protected JdbcClient jdbc;

    /** Every response is checked against the contract. */
    protected RestTestClient client;

    /** For protocol-level answers the contract does not describe (405, 413, 415). */
    protected RestTestClient unvalidatedClient;

    protected final String tenant = "tenant-" + UUID.randomUUID().toString().substring(0, 8);

    @DynamicPropertySource
    static void identityProvider(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", TestJwtIssuer::jwkSetUri);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> TestJwtIssuer.ISSUER);
    }

    @BeforeEach
    void createClients() {
        String baseUrl = "http://localhost:" + port;
        client = RestTestClient.bindToServer().baseUrl(baseUrl).requestInterceptor(new ContractValidator()).build();
        unvalidatedClient = RestTestClient.bindToServer().baseUrl(baseUrl).build();
    }

    /** A valid token of this test's tenant with read and write scopes. */
    protected String bearer() {
        return TestJwtIssuer.token().tenant(tenant).asBearer();
    }
}
