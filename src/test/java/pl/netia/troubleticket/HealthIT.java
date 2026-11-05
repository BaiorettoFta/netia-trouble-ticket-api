package pl.netia.troubleticket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;
import pl.netia.troubleticket.support.IntegrationTest;

/**
 * Health probes live on the management port, open without a token; the public port serves only the contract.
 */
class HealthIT extends IntegrationTest {

    @LocalManagementPort
    private int managementPort;

    private RestTestClient management;

    @BeforeEach
    void createManagementClient() {
        management = RestTestClient.bindToServer().baseUrl("http://localhost:" + managementPort).build();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void probesAreUpWithoutAToken(String path) {
        management.get().uri(path)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void otherActuatorEndpointsAreNotExposedEvenToAuthenticatedClients() {
        management.get().uri("/actuator/env")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void publicPortDoesNotServeActuator() {
        unvalidatedClient.get().uri("/actuator/health")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .exchange()
                .expectStatus().isNotFound();
    }
}
