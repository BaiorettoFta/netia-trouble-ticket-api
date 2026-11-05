package pl.netia.troubleticket.ticket.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;
import pl.netia.troubleticket.support.ApiSliceTest;
import pl.netia.troubleticket.support.ContractValidator;
import pl.netia.troubleticket.support.FakeJwtDecoder;
import pl.netia.troubleticket.ticket.application.NoteView;
import pl.netia.troubleticket.ticket.application.TroubleTicketNotFoundException;
import pl.netia.troubleticket.ticket.application.TroubleTicketService;
import pl.netia.troubleticket.ticket.application.TroubleTicketView;
import pl.netia.troubleticket.ticket.domain.StatusTransitionNotAllowedException;
import pl.netia.troubleticket.ticket.domain.TenantId;
import pl.netia.troubleticket.ticket.domain.TroubleTicketStatus;

@ApiSliceTest(controllers = TroubleTicketController.class)
class CloseTroubleTicketApiTest {

    private static final String ID = "TT-2026-000001";

    @MockitoBean
    private TroubleTicketService service;

    @Autowired
    private MockMvc mockMvc;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindTo(mockMvc)
                .defaultHeader(HttpHeaders.AUTHORIZATION, FakeJwtDecoder.bearer("tenant-a", "write"))
                .requestInterceptor(new ContractValidator())
                .build();
    }

    @Test
    void closesTheTicketOfTheCallersTenantAndReturnsIt() {
        given(service.close(new TenantId("tenant-a"), "test-client", ID)).willReturn(new TroubleTicketView(
                ID, "OK-123456", 987654321L, "Brak transmisji danych dla usługi klienta.",
                TroubleTicketStatus.CLOSED, List.of(new NoteView("NOTE-000001",
                        "Zgłoszenie utworzone przez konto API partnera.", Instant.parse("2026-03-19T10:15:00Z")))));

        patch("""
                { "status": "closed" }
                """)
                .expectStatus().isOk()
                .expectBody().json("""
                        {
                          "id": "TT-2026-000001",
                          "externalId": "OK-123456",
                          "serviceId": 987654321,
                          "description": "Brak transmisji danych dla usługi klienta.",
                          "status": "closed",
                          "notes": [
                            {
                              "id": "NOTE-000001",
                              "text": "Zgłoszenie utworzone przez konto API partnera.",
                              "date": "2026-03-19T10:15:00Z"
                            }
                          ]
                        }
                        """, JsonCompareMode.STRICT);
    }

    @ParameterizedTest(name = "status {0}")
    @ValueSource(strings = {"new", "acknowledged", "inProgress", "resolved", "rejected", "Closed", "CLOSED"})
    void rejectsEveryTargetStatusButClosed(String status) {
        patch("""
                { "status": "%s" }
                """.formatted(status))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.message").isEqualTo("Pole status ma niedozwoloną wartość dla tej operacji.");

        verifyNoInteractions(service);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{}", "{ \"status\": null }"})
    void requiresTheStatus(String body) {
        patch(body)
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("Pole status jest wymagane.");

        verifyNoInteractions(service);
    }

    @Test
    void rejectsFieldsOutsideTheContract() {
        patch("""
                { "status": "closed", "note": "Klient potwierdził zamknięcie zgłoszenia." }
                """)
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("Nieznane pole note.");

        verifyNoInteractions(service);
    }

    @Test
    void reportsAForbiddenTransitionWithItsOwnCode() {
        given(service.close(new TenantId("tenant-a"), "test-client", ID)).willThrow(
                new StatusTransitionNotAllowedException(ID, TroubleTicketStatus.REJECTED, TroubleTicketStatus.CLOSED));

        patch("""
                { "status": "closed" }
                """)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("STATUS_TRANSITION_NOT_ALLOWED")
                .jsonPath("$.message").isEqualTo("Nie można zmienić statusu zgłoszenia z rejected na closed.");
    }

    @Test
    void reportsMissingOrForeignTicketWithTheContractMessage() {
        given(service.close(new TenantId("tenant-a"), "test-client", ID))
                .willThrow(new TroubleTicketNotFoundException(ID));

        patch("""
                { "status": "closed" }
                """)
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TROUBLE_TICKET_NOT_FOUND")
                .jsonPath("$.message")
                .isEqualTo("Zgłoszenie nie istnieje albo nie jest widoczne w tenant scope użytkownika.");
    }

    private RestTestClient.ResponseSpec patch(String body) {
        return client.patch().uri("/api/v1/troubleTicket/" + ID)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }
}
