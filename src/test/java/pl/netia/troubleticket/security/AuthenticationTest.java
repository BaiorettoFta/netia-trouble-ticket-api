package pl.netia.troubleticket.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;
import pl.netia.troubleticket.support.ApiSliceTest;
import pl.netia.troubleticket.support.FakeJwtDecoder;
import pl.netia.troubleticket.ticket.application.TroubleTicketService;

@ApiSliceTest
class AuthenticationTest {

    @MockitoBean
    private TroubleTicketService troubleTicketService;

    @Autowired
    private MockMvc mockMvc;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindTo(mockMvc).build();
    }

    @Test
    void rejectsRequestWithoutTokenUsingContractErrorBody() {
        client.get().uri("/api/v1/troubleTicket")
                .header("X-Request-Id", "req-test-401")
                .exchange()
                .expectStatus().isUnauthorized()
                // RFC 6750: no error code when the request carries no token at all. Spring Security 7 also points
                // to the RFC 9728 protected resource metadata.
                .expectHeader().value(HttpHeaders.WWW_AUTHENTICATE,
                        value -> assertThat(value).startsWith("Bearer").doesNotContain("error="))
                .expectHeader().valueEquals("X-Request-Id", "req-test-401")
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json("""
                        {
                          "code": "UNAUTHORIZED",
                          "message": "Brak poprawnego Bearer tokenu.",
                          "requestId": "req-test-401"
                        }
                        """);
    }

    @Test
    void rejectsInvalidTokenAsInvalidToken() {
        client.get().uri("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().value(HttpHeaders.WWW_AUTHENTICATE,
                        value -> assertThat(value).startsWith("Bearer").contains("error=\"invalid_token\""))
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void letsRequestWithValidTokenThrough() {
        client.get().uri("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, FakeJwtDecoder.bearer("tenant-a", "read"))
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
    }
}
