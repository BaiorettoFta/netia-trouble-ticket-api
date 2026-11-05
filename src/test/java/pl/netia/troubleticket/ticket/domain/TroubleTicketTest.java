package pl.netia.troubleticket.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TroubleTicketTest {

    private static final Instant NOW = Instant.parse("2026-03-19T10:15:00Z");

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

    @Test
    void parsesEveryStatusCodeOfTheContract() {
        assertThat(TroubleTicketStatus.values())
                .extracting(TroubleTicketStatus::code)
                .containsExactly("new", "acknowledged", "inProgress", "resolved", "closed", "rejected");
        for (TroubleTicketStatus status : TroubleTicketStatus.values()) {
            assertThat(TroubleTicketStatus.fromCode(status.code())).isEqualTo(status);
        }
    }
}
