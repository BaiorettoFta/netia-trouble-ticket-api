package pl.netia.troubleticket.ticket.application;

import java.util.List;
import pl.netia.troubleticket.ticket.domain.TroubleTicket;
import pl.netia.troubleticket.ticket.domain.TroubleTicketStatus;

/**
 * Full representation of a ticket, built inside the transaction so nothing is loaded lazily afterwards.
 */
public record TroubleTicketView(String id, String externalId, long serviceId, String description,
        TroubleTicketStatus status, List<NoteView> notes) {

    static TroubleTicketView of(TroubleTicket ticket) {
        return new TroubleTicketView(ticket.getId(), ticket.getExternalId(), ticket.getServiceId(),
                ticket.getDescription(), ticket.getStatus(),
                ticket.getNotes().stream().map(NoteView::of).toList());
    }
}
