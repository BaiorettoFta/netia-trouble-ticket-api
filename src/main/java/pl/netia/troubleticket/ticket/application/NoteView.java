package pl.netia.troubleticket.ticket.application;

import java.time.Instant;
import pl.netia.troubleticket.ticket.domain.TroubleTicketNote;

/**
 * A note as returned by the API. Public note ids follow the contract examples: {@code NOTE-000001}.
 */
public record NoteView(String id, String text, Instant date) {

    static NoteView of(TroubleTicketNote note) {
        return new NoteView("NOTE-%06d".formatted(note.getId()), note.getText(), note.getCreatedAt());
    }
}
