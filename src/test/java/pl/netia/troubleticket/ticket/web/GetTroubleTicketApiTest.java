package pl.netia.troubleticket.ticket.web;

import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
import pl.netia.troubleticket.ticket.domain.TenantId;
import pl.netia.troubleticket.ticket.domain.TroubleTicketStatus;

@ApiSliceTest(controllers = TroubleTicketController.class)
class GetTroubleTicketApiTest {

    @MockitoBean
    private TroubleTicketService service;

    @Autowired
    private MockMvc mockMvc;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindTo(mockMvc)
                .defaultHeader(HttpHeaders.AUTHORIZATION, FakeJwtDecoder.bearer("tenant-a", "read"))
                .requestInterceptor(new ContractValidator())
                .build();
    }

    @Test
    void returnsTheFullTicketOfTheCallersTenant() {
        given(service.get(new TenantId("tenant-a"), "TT-2026-000001")).willReturn(new TroubleTicketView(
                "TT-2026-000001", "OK-123456", 987654321L, "Brak transmisji danych dla usługi klienta.",
                TroubleTicketStatus.RESOLVED, List.of(
                        new NoteView("NOTE-000001", "Zgłoszenie utworzone przez konto API partnera.",
                                Instant.parse("2026-03-19T10:15:00Z")),
                        new NoteView("NOTE-000002", "Potwierdzono diagnozę po stronie operatora infrastruktury.",
                                Instant.parse("2026-03-19T10:30:00.250Z")))));

        client.get().uri("/api/v1/troubleTicket/TT-2026-000001")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        {
                          "id": "TT-2026-000001",
                          "externalId": "OK-123456",
                          "serviceId": 987654321,
                          "description": "Brak transmisji danych dla usługi klienta.",
                          "status": "resolved",
                          "notes": [
                            {
                              "id": "NOTE-000001",
                              "text": "Zgłoszenie utworzone przez konto API partnera.",
                              "date": "2026-03-19T10:15:00Z"
                            },
                            {
                              "id": "NOTE-000002",
                              "text": "Potwierdzono diagnozę po stronie operatora infrastruktury.",
                              "date": "2026-03-19T10:30:00.25Z"
                            }
                          ]
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    void reportsMissingOrForeignTicketWithTheContractMessage() {
        given(service.get(new TenantId("tenant-a"), "TT-2026-999999"))
                .willThrow(new TroubleTicketNotFoundException("TT-2026-999999"));

        client.get().uri("/api/v1/troubleTicket/TT-2026-999999")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TROUBLE_TICKET_NOT_FOUND")
                .jsonPath("$.message")
                .isEqualTo("Zgłoszenie nie istnieje albo nie jest widoczne w tenant scope użytkownika.");
    }
}
