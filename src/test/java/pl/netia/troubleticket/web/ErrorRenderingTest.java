package pl.netia.troubleticket.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;
import pl.netia.troubleticket.support.ApiSliceTest;
import pl.netia.troubleticket.support.FakeJwtDecoder;

@ApiSliceTest(controllers = ProbeController.class)
@Import(ProbeController.class)
class ErrorRenderingTest {

    private static final String TOKEN = FakeJwtDecoder.bearer("tenant-a", "read", "write");

    @Autowired
    private MockMvc mockMvc;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindTo(mockMvc)
                .defaultHeader(HttpHeaders.AUTHORIZATION, TOKEN)
                .build();
    }

    @Test
    void unknownPathIsNotFound() {
        client.get().uri("/api/v1/unknown")
                .header("X-Request-Id", "req-test-404")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().json("""
                        { "code": "NOT_FOUND", "message": "Zasób nie istnieje.", "requestId": "req-test-404" }
                        """);
    }

    @Test
    void unsupportedMethodIsMethodNotAllowedWithAllowHeader() {
        client.put().uri(ProbeController.PATH)
                .exchange()
                .expectStatus().isEqualTo(405)
                .expectHeader().value(HttpHeaders.ALLOW, allow -> assertThat(allow).contains("POST"))
                .expectBody().jsonPath("$.code").isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void unsupportedContentTypeIsRejected() {
        client.post().uri(ProbeController.PATH)
                .contentType(MediaType.TEXT_PLAIN)
                .body("externalId=OK-1")
                .exchange()
                .expectStatus().isEqualTo(415)
                .expectBody().jsonPath("$.code").isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void clientThatDoesNotAcceptJsonGetsNotAcceptableWithoutBody() {
        client.get().uri(ProbeController.PATH)
                .accept(MediaType.APPLICATION_XML)
                .exchange()
                .expectStatus().isEqualTo(406)
                .expectBody().isEmpty();
    }

    @Test
    void unexpectedFailureHidesInternalDetails() {
        String body = client.get().uri(ProbeController.PATH + "/failure")
                .exchange()
                .expectStatus().isEqualTo(500)
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(body)
                .contains("\"code\":\"INTERNAL_ERROR\"", "Wystąpił nieoczekiwany błąd.")
                .doesNotContain("db-internal-01", "app_admin", "IllegalStateException");
    }

    @Test
    void reportsEveryMissingFieldInOneStableMessage() {
        client.post().uri(ProbeController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.message").isEqualTo("Pole description jest wymagane. Pole externalId jest wymagane. "
                        + "Pole note jest wymagane. Pole serviceId jest wymagane. Pole status jest wymagane.");
    }

    @Test
    void reportsSchemaConstraintViolations() {
        client.post().uri(ProbeController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        { "externalId": "", "serviceId": 0, "description": "", "status": "new", "note": "" }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.message").isEqualTo("Pole description nie może być puste. "
                        + "Pole externalId nie może być puste. Pole note nie może być puste. "
                        + "Pole serviceId musi być liczbą całkowitą nie mniejszą niż 1.");
    }

    @Test
    void malformedJsonIsValidationError() {
        client.post().uri(ProbeController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{ \"externalId\": ")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().json("""
                        { "code": "VALIDATION_ERROR", "message": "Treść żądania nie jest poprawnym dokumentem JSON." }
                        """);
    }

    @Test
    void missingBodyIsValidationError() {
        client.post().uri(ProbeController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("Treść żądania nie jest poprawnym dokumentem JSON.");
    }
}
