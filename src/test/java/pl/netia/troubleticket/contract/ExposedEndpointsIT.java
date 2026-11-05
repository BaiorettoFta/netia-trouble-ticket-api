package pl.netia.troubleticket.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import pl.netia.troubleticket.support.ContractDocument;
import pl.netia.troubleticket.support.IntegrationTest;
import pl.netia.troubleticket.support.TestJwtIssuer;

/**
 * The public surface of the application is exactly the contract: no operation is missing and none is added.
 */
class ExposedEndpointsIT extends IntegrationTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void applicationExposesExactlyTheOperationsOfTheContract() {
        Set<String> exposed = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((mapping, handler) -> {
            // Framework endpoints such as /error are not part of the API.
            if (handler.getBeanType().getPackageName().startsWith("pl.netia.troubleticket")) {
                mapping.getMethodsCondition().getMethods().forEach(method -> mapping.getPatternValues()
                        .forEach(path -> exposed.add(method.name() + " " + path)));
            }
        });

        assertThat(exposed).containsExactlyInAnyOrderElementsOf(ContractDocument.operations());
    }

    /**
     * Besides the contract, Spring Security publishes the standard OAuth 2.0 resource metadata (RFC 9728), which
     * must describe this service truthfully.
     */
    @Test
    void publishesTruthfulOAuthResourceMetadata() {
        unvalidatedClient.get().uri("/.well-known/oauth-protected-resource")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.authorization_servers").isEqualTo(List.of(TestJwtIssuer.ISSUER))
                .jsonPath("$.scopes_supported").isEqualTo(List.of("trouble-ticket:read", "trouble-ticket:write"))
                .jsonPath("$.bearer_methods_supported").isEqualTo(List.of("header"))
                .jsonPath("$.tls_client_certificate_bound_access_tokens").isEqualTo(false);
    }

    @Test
    void otherApiVersionsDoNotExist() {
        unvalidatedClient.get().uri("/api/v2/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("NOT_FOUND");
    }
}
