package pl.netia.troubleticket.support;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import pl.netia.troubleticket.openapi.model.TroubleTicketDto;

/**
 * The whole application on a random port with PostgreSQL in Testcontainers and real token verification.
 *
 * <p>Every test gets its own random tenant, so tests are isolated without cleaning tables, and tenant
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

    protected long givenActiveService() {
        return givenService(tenant, "active");
    }

    /** Inserts a service into the inventory stand-in; ids are above the int range to keep JSON numbers long. */
    protected long givenService(String tenantId, String status) {
        long serviceId = ThreadLocalRandom.current().nextLong(10_000_000_000L, 9_000_000_000_000L);
        jdbc.sql("insert into service_instance (id, tenant_id, status) values (:id, :tenantId, :status)")
                .param("id", serviceId)
                .param("tenantId", tenantId)
                .param("status", status)
                .update();
        return serviceId;
    }

    protected static String createRequest(String externalId, long serviceId) {
        return """
                {
                  "externalId": "%s",
                  "serviceId": %d,
                  "description": "Brak transmisji danych dla usługi klienta.",
                  "status": "new",
                  "note": "Zgłoszenie utworzone przez konto API partnera."
                }
                """.formatted(externalId, serviceId);
    }

    /** Creates a ticket in this test's tenant and returns its id. */
    protected String givenTicket(String externalId, long serviceId) {
        TroubleTicketDto ticket = client.post().uri("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(createRequest(externalId, serviceId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TroubleTicketDto.class).returnResult().getResponseBody();
        return Objects.requireNonNull(ticket).id();
    }

    /** Sets a status the way the ticket handling system (SOZ) would; the public API cannot do it. */
    protected void givenStatusSetBySoz(String ticketId, String status) {
        jdbc.sql("update trouble_ticket set status = :status where id = :id")
                .param("status", status)
                .param("id", ticketId)
                .update();
    }
}
