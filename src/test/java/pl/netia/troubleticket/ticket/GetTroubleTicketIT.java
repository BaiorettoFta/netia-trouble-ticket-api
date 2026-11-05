package pl.netia.troubleticket.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;
import pl.netia.troubleticket.openapi.model.ErrorDto;
import pl.netia.troubleticket.support.IntegrationTest;
import pl.netia.troubleticket.support.TestJwtIssuer;

class GetTroubleTicketIT extends IntegrationTest {

    @Test
    void returnsTheFullTicketWithItsNotes() {
        long serviceId = givenActiveService();
        String id = givenTicket("OK-1", serviceId);
        givenStatusSetBySoz(id, "resolved");

        get(id, bearer())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id)
                .jsonPath("$.externalId").isEqualTo("OK-1")
                .jsonPath("$.serviceId").isEqualTo(serviceId)
                .jsonPath("$.description").isEqualTo("Brak transmisji danych dla usługi klienta.")
                .jsonPath("$.status").isEqualTo("resolved")
                .jsonPath("$.notes.length()").isEqualTo(1)
                .jsonPath("$.notes[0].text").isEqualTo("Zgłoszenie utworzone przez konto API partnera.");
    }

    @Test
    void ticketOfAnotherTenantLooksExactlyLikeOneThatDoesNotExist() {
        String id = givenTicket("OK-2", givenActiveService());
        String otherTenant = TestJwtIssuer.token().tenant(tenant + "-other").asBearer();

        ErrorDto foreign = get(id, otherTenant)
                .expectStatus().isNotFound()
                .expectBody(ErrorDto.class).returnResult().getResponseBody();
        ErrorDto missing = get("TT-2026-999999", bearer())
                .expectStatus().isNotFound()
                .expectBody(ErrorDto.class).returnResult().getResponseBody();

        assertThat(foreign).usingRecursiveComparison().ignoringFields("requestId").isEqualTo(missing);
        assertThat(foreign.code()).isEqualTo("TROUBLE_TICKET_NOT_FOUND");
    }

    @Test
    void anyUnknownIdIsNotFound() {
        get("not-a-ticket-id", bearer())
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("TROUBLE_TICKET_NOT_FOUND");
    }

    @Test
    void readScopeIsEnough() {
        String id = givenTicket("OK-3", givenActiveService());

        get(id, TestJwtIssuer.token().tenant(tenant).scopes("trouble-ticket:read").asBearer())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo(id);
    }

    @Test
    void writeScopeAloneDoesNotAllowReading() {
        String id = givenTicket("OK-4", givenActiveService());

        get(id, TestJwtIssuer.token().tenant(tenant).scopes("trouble-ticket:write").asBearer())
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    private RestTestClient.ResponseSpec get(String id, String bearer) {
        return client.get().uri("/api/v1/troubleTicket/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .exchange();
    }
}
