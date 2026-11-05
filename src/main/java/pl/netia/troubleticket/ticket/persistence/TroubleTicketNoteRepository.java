package pl.netia.troubleticket.ticket.persistence;

import org.springframework.data.repository.Repository;
import pl.netia.troubleticket.ticket.domain.TroubleTicketNote;

/**
 * Notes are only ever added, never changed or removed, so this repository can do nothing else. A note is inserted
 * as a row of its own: adding it does not write the ticket row.
 */
public interface TroubleTicketNoteRepository extends Repository<TroubleTicketNote, Long> {

    TroubleTicketNote save(TroubleTicketNote note);
}
