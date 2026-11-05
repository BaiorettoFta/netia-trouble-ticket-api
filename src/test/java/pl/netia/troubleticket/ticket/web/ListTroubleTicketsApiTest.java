package pl.netia.troubleticket.ticket.web;

import static org.mockito.BDDMockito.given;

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
import pl.netia.troubleticket.ticket.application.TroubleTicketService;
import pl.netia.troubleticket.ticket.domain.TenantId;
import pl.netia.troubleticket.ticket.domain.TroubleTicketStatus;
import pl.netia.troubleticket.ticket.domain.TroubleTicketSummary;

@ApiSliceTest(controllers = TroubleTicketController.class)
class ListTroubleTicketsApiTest {

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
    void listsOnlyTheFourFieldsOfTheContract() {
        given(service.list(new TenantId("tenant-a"))).willReturn(List.of(
                new TroubleTicketSummary("OK-123456", 987654321L, "Brak transmisji danych dla usługi klienta.",
                        TroubleTicketStatus.ACKNOWLEDGED),
                new TroubleTicketSummary("OK-123457", 987654322L, "Niestabilne parametry usługi.",
                        TroubleTicketStatus.RESOLVED)));

        client.get().uri("/api/v1/troubleTicket")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        [
                          {
                            "externalId": "OK-123456",
                            "serviceId": 987654321,
                            "description": "Brak transmisji danych dla usługi klienta.",
                            "status": "acknowledged"
                          },
                          {
                            "externalId": "OK-123457",
                            "serviceId": 987654322,
                            "description": "Niestabilne parametry usługi.",
                            "status": "resolved"
                          }
                        ]
                        """, JsonCompareMode.STRICT);
    }

    @Test
    void returnsEmptyArrayWhenTheTenantHasNoTickets() {
        given(service.list(new TenantId("tenant-a"))).willReturn(List.of());

        client.get().uri("/api/v1/troubleTicket")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]", JsonCompareMode.STRICT);
    }
}
