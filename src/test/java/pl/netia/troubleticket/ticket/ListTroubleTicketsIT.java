package pl.netia.troubleticket.ticket;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;
import pl.netia.troubleticket.support.IntegrationTest;
import pl.netia.troubleticket.support.TestJwtIssuer;

class ListTroubleTicketsIT extends IntegrationTest {

    @Test
    void listsTicketsOfTheCallersTenantOnlyOldestFirst() {
        long serviceId = givenActiveService();
        givenTicket("OK-1", serviceId);
        String second = givenTicket("OK-2", serviceId);
        givenTicket("OK-3", serviceId);
        givenStatusSetBySoz(second, "inProgress");
        String otherTenant = tenant + "-other";
        long otherService = givenService(otherTenant, "active");
        client.post().uri("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, TestJwtIssuer.token().tenant(otherTenant).asBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(createRequest("OK-OTHER", otherService))
                .exchange()
                .expectStatus().isCreated();

        list("/api/v1/troubleTicket")
                .expectStatus().isOk()
                .expectBody().json("""
                        [
                          { "externalId": "OK-1", "serviceId": %1$d,
                            "description": "Brak transmisji danych dla usługi klienta.", "status": "new" },
                          { "externalId": "OK-2", "serviceId": %1$d,
                            "description": "Brak transmisji danych dla usługi klienta.", "status": "inProgress" },
                          { "externalId": "OK-3", "serviceId": %1$d,
                            "description": "Brak transmisji danych dla usługi klienta.", "status": "new" }
                        ]
                        """.formatted(serviceId), JsonCompareMode.STRICT);
    }

    @Test
    void returnsEmptyArrayForTenantWithoutTickets() {
        list("/api/v1/troubleTicket")
                .expectStatus().isOk()
                .expectBody().json("[]", JsonCompareMode.STRICT);
    }

    @Test
    void ignoresQueryParametersBecauseTheContractHasNoPagingOrFilters() {
        givenTicket("OK-1", givenActiveService());

        list("/api/v1/troubleTicket?status=closed&page=2&limit=1")
                .expectStatus().isOk()
                .expectBody().jsonPath("$.length()").isEqualTo(1);
    }

    private RestTestClient.ResponseSpec list(String uri) {
        return client.get().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .exchange();
    }
}
