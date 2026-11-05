package pl.netia.troubleticket.ticket.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
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
import pl.netia.troubleticket.ticket.domain.NoteText;
import pl.netia.troubleticket.ticket.domain.TenantId;

@ApiSliceTest(controllers = TroubleTicketNoteController.class)
class AddTroubleTicketNoteApiTest {

    private static final String ID = "TT-2026-000001";
    private static final String TEXT = "Klient prosi o potwierdzenie planowanego terminu zamknięcia.";

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
    void addsTheNoteAndReturnsItWithoutLocation() {
        given(service.addNote(new TenantId("tenant-a"), "test-client", ID, new NoteText(TEXT)))
                .willReturn(new NoteView("NOTE-000003", TEXT, Instant.parse("2026-03-19T10:45:00Z")));

        post("""
                { "text": "%s" }
                """.formatted(TEXT))
                .expectStatus().isCreated()
                .expectHeader().doesNotExist(HttpHeaders.LOCATION)
                .expectBody().json("""
                        {
                          "id": "NOTE-000003",
                          "text": "Klient prosi o potwierdzenie planowanego terminu zamknięcia.",
                          "date": "2026-03-19T10:45:00Z"
                        }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    void passesWhitespaceOnlyTextBecauseTheContractAllowsIt() {
        given(service.addNote(new TenantId("tenant-a"), "test-client", ID, new NoteText("   ")))
                .willReturn(new NoteView("NOTE-000004", "   ", Instant.parse("2026-03-19T10:46:00Z")));

        post("""
                { "text": "   " }
                """)
                .expectStatus().isCreated();

        verify(service).addNote(new TenantId("tenant-a"), "test-client", ID, new NoteText("   "));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{}", "{ \"text\": null }"})
    void requiresTheText(String body) {
        post(body)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.message").isEqualTo("Pole text jest wymagane.");

        verifyNoInteractions(service);
    }

    @Test
    void rejectsEmptyText() {
        post("""
                { "text": "" }
                """)
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("Pole text nie może być puste.");

        verifyNoInteractions(service);
    }

    @Test
    void cannotChangeTheStatusOfTheTicket() {
        post("""
                { "text": "Klient potwierdził zamknięcie zgłoszenia.", "status": "closed" }
                """)
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("Nieznane pole status.");

        verifyNoInteractions(service);
    }

    @Test
    void rejectsTextTheDatabaseCannotStoreBeforeCallingTheService() {
        String nulCharacter = "\\" + "u0000";

        post("""
                { "text": "Notatka%s" }
                """.formatted(nulCharacter))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("Pole text zawiera niedozwolone znaki.");

        verifyNoInteractions(service);
    }

    @Test
    void reportsMissingOrForeignTicketWithTheContractMessage() {
        given(service.addNote(new TenantId("tenant-a"), "test-client", ID, new NoteText(TEXT)))
                .willThrow(new TroubleTicketNotFoundException(ID));

        post("""
                { "text": "%s" }
                """.formatted(TEXT))
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TROUBLE_TICKET_NOT_FOUND")
                .jsonPath("$.message")
                .isEqualTo("Zgłoszenie nie istnieje albo nie jest widoczne w tenant scope użytkownika.");
    }

    private RestTestClient.ResponseSpec post(String body) {
        return client.post().uri("/api/v1/troubleTicket/{id}/note", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }
}
