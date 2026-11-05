package pl.netia.troubleticket.ticket.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import pl.netia.troubleticket.ticket.application.CreationResult.AlreadyExists;
import pl.netia.troubleticket.ticket.application.CreationResult.Created;
import pl.netia.troubleticket.ticket.application.NoteView;
import pl.netia.troubleticket.ticket.application.ServiceNotFoundException;
import pl.netia.troubleticket.ticket.application.TroubleTicketService;
import pl.netia.troubleticket.ticket.application.TroubleTicketView;
import pl.netia.troubleticket.ticket.domain.NewTroubleTicket;
import pl.netia.troubleticket.ticket.domain.TenantId;
import pl.netia.troubleticket.ticket.domain.TroubleTicketStatus;

@ApiSliceTest(controllers = TroubleTicketController.class)
class CreateTroubleTicketApiTest {

    private static final String REQUEST = """
            {
              "externalId": "OK-123456",
              "serviceId": 987654321,
              "description": "Brak transmisji danych dla usługi klienta.",
              "status": "new",
              "note": "Zgłoszenie utworzone przez konto API partnera."
            }
            """;

    private static final TroubleTicketView TICKET = new TroubleTicketView("TT-2026-000001", "OK-123456",
            987654321L, "Brak transmisji danych dla usługi klienta.", TroubleTicketStatus.NEW,
            List.of(new NoteView("NOTE-000001", "Zgłoszenie utworzone przez konto API partnera.",
                    Instant.parse("2026-03-19T10:15:00Z"))));

    private static final String TICKET_JSON = """
            {
              "id": "TT-2026-000001",
              "externalId": "OK-123456",
              "serviceId": 987654321,
              "description": "Brak transmisji danych dla usługi klienta.",
              "status": "new",
              "notes": [
                {
                  "id": "NOTE-000001",
                  "text": "Zgłoszenie utworzone przez konto API partnera.",
                  "date": "2026-03-19T10:15:00Z"
                }
              ]
            }
            """;

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
    void createsTicketInTheTenantOfTheToken() {
        given(service.create(any(), any(), any())).willReturn(new Created(TICKET));

        post(REQUEST)
                .expectStatus().isCreated()
                .expectHeader().valueEquals(HttpHeaders.LOCATION, "/api/v1/troubleTicket/TT-2026-000001")
                .expectBody().json(TICKET_JSON, JsonCompareMode.STRICT);

        verify(service).create(new TenantId("tenant-a"), "test-client", new NewTroubleTicket("OK-123456",
                987654321L, "Brak transmisji danych dla usługi klienta.",
                "Zgłoszenie utworzone przez konto API partnera."));
    }

    @Test
    void returnsExistingTicketWithStatus200AndLocation() {
        given(service.create(any(), any(), any())).willReturn(new AlreadyExists(TICKET));

        post(REQUEST)
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.LOCATION, "/api/v1/troubleTicket/TT-2026-000001")
                .expectBody().json(TICKET_JSON, JsonCompareMode.STRICT);
    }

    @Test
    void reportsUnknownServiceWithTheContractMessage() {
        given(service.create(any(), any(), any())).willThrow(new ServiceNotFoundException(987654321L));

        post(REQUEST)
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("SERVICE_NOT_FOUND")
                .jsonPath("$.message").isEqualTo("Wskazana usługa nie istnieje, nie jest aktywna albo nie należy "
                        + "do tenant scope użytkownika.");
    }

    @Test
    void rejectsTextTheDatabaseCannotStoreBeforeCallingTheService() {
        String nulCharacter = "\\" + "u0000";

        post(REQUEST.replace("Brak transmisji", "Brak" + nulCharacter + " transmisji"))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.message").isEqualTo("Pole description zawiera niedozwolone znaki.");

        verifyNoInteractions(service);
    }

    @Test
    void rejectsExternalIdLongerThan255Characters() {
        post(REQUEST.replace("OK-123456", "x".repeat(256)))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("Pole externalId może mieć najwyżej 255 znaków.");

        verifyNoInteractions(service);
    }

    private RestTestClient.ResponseSpec post(String body) {
        return client.post().uri("/api/v1/troubleTicket")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }
}
