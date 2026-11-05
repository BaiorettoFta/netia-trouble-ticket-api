package pl.netia.troubleticket.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class TroubleTicketTest {

    private static final Instant NOW = Instant.parse("2026-03-19T10:15:00Z");
    private static final Instant LATER = Instant.parse("2026-03-19T11:00:00Z");

    private final NewTroubleTicket newTicket = new NewTroubleTicket(
            "OK-123456", 987654321L, "Brak transmisji danych dla usługi klienta.",
            "Zgłoszenie utworzone przez konto API partnera.");

    @Test
    void opensInStatusNewWithRequestNoteAsFirstNote() {
        TroubleTicket ticket = TroubleTicket.open("TT-2026-000001", new TenantId("tenant-a"), newTicket, "partner-a",
                NOW);

        assertThat(ticket.getId()).isEqualTo("TT-2026-000001");
        assertThat(ticket.getTenantId()).isEqualTo(new TenantId("tenant-a"));
        assertThat(ticket.getExternalId()).isEqualTo("OK-123456");
        assertThat(ticket.getServiceId()).isEqualTo(987654321L);
        assertThat(ticket.getDescription()).isEqualTo("Brak transmisji danych dla usługi klienta.");
        assertThat(ticket.getStatus()).isEqualTo(TroubleTicketStatus.NEW);
        assertThat(ticket.getNotes()).singleElement().satisfies(note -> {
            assertThat(note.getText()).isEqualTo("Zgłoszenie utworzone przez konto API partnera.");
            assertThat(note.getCreatedBy()).isEqualTo("partner-a");
            assertThat(note.getCreatedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void recordsWhoCreatedTheTicketAndWhen() {
        TroubleTicket ticket = TroubleTicket.open("TT-2026-000001", new TenantId("tenant-a"), newTicket, "partner-a",
                NOW);

        assertThat(ticket.getCreatedBy()).isEqualTo("partner-a");
        assertThat(ticket.getCreatedAt()).isEqualTo(NOW);
        assertThat(ticket.getUpdatedBy()).isEqualTo("partner-a");
        assertThat(ticket.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void isNewUntilStored() {
        TroubleTicket ticket = TroubleTicket.open("TT-2026-000001", new TenantId("tenant-a"), newTicket, "partner-a",
                NOW);

        assertThat(ticket.isNew()).isTrue();
        ticket.markStored();
        assertThat(ticket.isNew()).isFalse();
    }

    @Test
    void notesCannotBeChangedFromOutside() {
        TroubleTicket ticket = TroubleTicket.open("TT-2026-000001", new TenantId("tenant-a"), newTicket, "partner-a",
                NOW);

        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> ticket.getNotes().clear());
    }

    @ParameterizedTest(name = "from {0}")
    @EnumSource(value = TroubleTicketStatus.class, names = {"NEW", "ACKNOWLEDGED", "IN_PROGRESS", "RESOLVED"})
    void closesAnOpenTicketAndRecordsWhoClosedIt(TroubleTicketStatus status) {
        TroubleTicket ticket = ticketInStatus(status);

        ticket.close("partner-b", LATER);

        assertThat(ticket.getStatus()).isEqualTo(TroubleTicketStatus.CLOSED);
        assertThat(ticket.getUpdatedBy()).isEqualTo("partner-b");
        assertThat(ticket.getUpdatedAt()).isEqualTo(LATER);
        assertThat(ticket.getCreatedBy()).isEqualTo("partner-a");
        assertThat(ticket.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void closingAClosedTicketChangesNothing() {
        TroubleTicket ticket = ticketInStatus(TroubleTicketStatus.NEW);
        ticket.close("partner-a", NOW);

        ticket.close("partner-b", LATER);

        assertThat(ticket.getStatus()).isEqualTo(TroubleTicketStatus.CLOSED);
        assertThat(ticket.getUpdatedBy()).isEqualTo("partner-a");
        assertThat(ticket.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectedTicketCannotBeClosed() {
        TroubleTicket ticket = ticketInStatus(TroubleTicketStatus.REJECTED);

        assertThatExceptionOfType(StatusTransitionNotAllowedException.class)
                .isThrownBy(() -> ticket.close("partner-b", LATER))
                .satisfies(exception -> {
                    assertThat(exception.currentStatus()).isEqualTo(TroubleTicketStatus.REJECTED);
                    assertThat(exception.requestedStatus()).isEqualTo(TroubleTicketStatus.CLOSED);
                });
        assertThat(ticket.getStatus()).isEqualTo(TroubleTicketStatus.REJECTED);
        assertThat(ticket.getUpdatedBy()).isEqualTo("partner-a");
        assertThat(ticket.getUpdatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest(name = "in status {0}")
    @EnumSource(TroubleTicketStatus.class)
    void addsANoteInAnyStatusWithoutChangingTheTicket(TroubleTicketStatus status) {
        TroubleTicket ticket = ticketInStatus(status);

        TroubleTicketNote note = ticket.addNote(new NoteText("Klient prosi o potwierdzenie terminu."), "partner-b",
                LATER);

        assertThat(note.getText()).isEqualTo("Klient prosi o potwierdzenie terminu.");
        assertThat(note.getCreatedBy()).isEqualTo("partner-b");
        assertThat(note.getCreatedAt()).isEqualTo(LATER);
        assertThat(ticket.getNotes()).hasSize(2).last().isSameAs(note);
        assertThat(ticket.getStatus()).isEqualTo(status);
        assertThat(ticket.getUpdatedBy()).isEqualTo("partner-a");
        assertThat(ticket.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void parsesEveryStatusCodeOfTheContract() {
        assertThat(TroubleTicketStatus.values())
                .extracting(TroubleTicketStatus::code)
                .containsExactly("new", "acknowledged", "inProgress", "resolved", "closed", "rejected");
        for (TroubleTicketStatus status : TroubleTicketStatus.values()) {
            assertThat(TroubleTicketStatus.fromCode(status.code())).isEqualTo(status);
        }
    }

    /**
     * Statuses other than {@code new} are set by the ticket handling system directly in the database; the domain
     * offers no way to set them, so the test sets the field the way loading from the database would.
     */
    private TroubleTicket ticketInStatus(TroubleTicketStatus status) {
        TroubleTicket ticket = TroubleTicket.open("TT-2026-000001", new TenantId("tenant-a"), newTicket, "partner-a",
                NOW);
        ReflectionTestUtils.setField(ticket, "status", status);
        return ticket;
    }
}
