package pl.netia.troubleticket.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import pl.netia.troubleticket.openapi.model.NoteDto;
import pl.netia.troubleticket.openapi.model.TroubleTicketDto;
import pl.netia.troubleticket.support.IntegrationTest;
import pl.netia.troubleticket.support.TestJwtIssuer;

class AddTroubleTicketNoteIT extends IntegrationTest {

    private static final String INITIAL_NOTE = "Zgłoszenie utworzone przez konto API partnera.";

    @Test
    void addsANoteThatAppearsLastInTheTicket() {
        String id = givenTicket("OK-1", givenActiveService());

        NoteDto note = addNote(id, "Klient prosi o potwierdzenie planowanego terminu zamknięcia.", bearer())
                .expectStatus().isCreated()
                .expectHeader().doesNotExist(HttpHeaders.LOCATION)
                .expectBody(NoteDto.class).returnResult().getResponseBody();

        assertThat(Objects.requireNonNull(note).id()).matches("NOTE-\\d{6,}");
        assertThat(note.text()).isEqualTo("Klient prosi o potwierdzenie planowanego terminu zamknięcia.");
        TroubleTicketDto ticket = get(id);
        assertThat(ticket.status().getValue()).isEqualTo("new");
        assertThat(ticket.notes()).hasSize(2).last().isEqualTo(note);
    }

    @Test
    void keepsNotesInTheOrderTheyWereAdded() {
        String id = givenTicket("OK-2", givenActiveService());

        addNote(id, "Pierwsza notatka klienta.", bearer()).expectStatus().isCreated();
        addNote(id, "Druga notatka klienta.", bearer()).expectStatus().isCreated();

        assertThat(get(id).notes()).extracting(NoteDto::text)
                .containsExactly(INITIAL_NOTE, "Pierwsza notatka klienta.", "Druga notatka klienta.");
    }

    @ParameterizedTest(name = "in status {0}")
    @ValueSource(strings = {"closed", "rejected"})
    void addsNotesToTicketsInAFinalStatusAndKeepsTheStatus(String status) {
        String id = givenTicket("OK-3", givenActiveService());
        givenStatusSetBySoz(id, status);

        addNote(id, "Klient prosi o ponowną weryfikację.", bearer()).expectStatus().isCreated();

        assertThat(get(id).status().getValue()).isEqualTo(status);
    }

    @Test
    void recordsTheAuthorAndLeavesTheTicketRowUntouched() {
        String id = givenTicket("OK-4", givenActiveService());
        Map<String, Object> ticketBefore = ticketRow(id);

        addNote(id, "Notatka operatora partnera.",
                TestJwtIssuer.token().tenant(tenant).subject("partner-a-operator").asBearer())
                .expectStatus().isCreated();

        assertThat(ticketRow(id)).isEqualTo(ticketBefore);
        assertThat(noteAuthors(id)).containsExactly("partner-a", "partner-a-operator");
    }

    @Test
    void ticketOfAnotherTenantIsNotFoundAndGetsNoNote() {
        String id = givenTicket("OK-5", givenActiveService());

        addNote(id, "Notatka z innego tenanta.", TestJwtIssuer.token().tenant(tenant + "-other").asBearer())
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("TROUBLE_TICKET_NOT_FOUND");

        assertThat(noteAuthors(id)).hasSize(1);
    }

    @Test
    void unknownTicketIsNotFound() {
        addNote("TT-2026-999999", "Notatka.", bearer())
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("TROUBLE_TICKET_NOT_FOUND");
    }

    @Test
    void createdAgainAfterANoteTheTicketIsReturnedWithIt() {
        long serviceId = givenActiveService();
        String id = givenTicket("OK-6", serviceId);
        addNote(id, "Weryfikacja zgłoszenia w toku.", bearer()).expectStatus().isCreated();

        client.post().uri("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(createRequest("OK-6", serviceId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id)
                .jsonPath("$.notes.length()").isEqualTo(2)
                .jsonPath("$.notes[1].text").isEqualTo("Weryfikacja zgłoszenia w toku.");
    }

    @Test
    void acceptsWhitespaceOnlyTextBecauseTheContractAllowsIt() {
        String id = givenTicket("OK-7", givenActiveService());

        addNote(id, "   ", bearer())
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.text").isEqualTo("   ");
    }

    @Test
    void rejectsTextTheDatabaseCannotStoreInsteadOfFailing() {
        String id = givenTicket("OK-8", givenActiveService());
        String nulCharacter = "\\" + "u0000";

        addNote(id, "Notatka" + nulCharacter, bearer())
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("Pole text zawiera niedozwolone znaki.");
    }

    /**
     * The text is put into the JSON as is, so callers can pass JSON escapes such as a NUL character.
     */
    private RestTestClient.ResponseSpec addNote(String id, String text, String bearer) {
        return client.post().uri("/api/v1/troubleTicket/{id}/note", id)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        { "text": "%s" }
                        """.formatted(text))
                .exchange();
    }

    private TroubleTicketDto get(String id) {
        return Objects.requireNonNull(client.get().uri("/api/v1/troubleTicket/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody(TroubleTicketDto.class).returnResult().getResponseBody());
    }

    private Map<String, Object> ticketRow(String id) {
        return jdbc.sql("select * from trouble_ticket where id = :id").param("id", id).query().singleRow();
    }

    private List<String> noteAuthors(String id) {
        return jdbc.sql("select created_by from trouble_ticket_note where trouble_ticket_id = :id order by id")
                .param("id", id)
                .query(String.class)
                .list();
    }
}
