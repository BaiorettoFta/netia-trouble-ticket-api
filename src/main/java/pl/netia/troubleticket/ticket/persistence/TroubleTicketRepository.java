package pl.netia.troubleticket.ticket.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.netia.troubleticket.ticket.domain.TroubleTicket;

/**
 * Every query is scoped by tenant: a ticket of another tenant is simply not found.
 */
public interface TroubleTicketRepository extends JpaRepository<TroubleTicket, String> {

    /**
     * The ticket with its notes, in one query.
     */
    @Query("""
            select ticket from TroubleTicket ticket left join fetch ticket.notes
            where ticket.tenantId = :tenantId and ticket.externalId = :externalId
            """)
    Optional<TroubleTicket> findWithNotesByTenantAndExternalId(String tenantId, String externalId);
}
