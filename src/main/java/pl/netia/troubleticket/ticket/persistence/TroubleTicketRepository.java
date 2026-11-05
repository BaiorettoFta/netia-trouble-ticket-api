package pl.netia.troubleticket.ticket.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.netia.troubleticket.ticket.domain.TroubleTicket;
import pl.netia.troubleticket.ticket.domain.TroubleTicketSummary;

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

    /**
     * The ticket with its notes, in one query.
     */
    @Query("""
            select ticket from TroubleTicket ticket left join fetch ticket.notes
            where ticket.id = :id and ticket.tenantId = :tenantId
            """)
    Optional<TroubleTicket> findWithNotesByIdAndTenant(String id, String tenantId);

    /**
     * All tickets of the tenant in creation order, as one projection query: no entities, no notes.
     */
    @Query("""
            select new pl.netia.troubleticket.ticket.domain.TroubleTicketSummary(
                ticket.externalId, ticket.serviceId, ticket.description, ticket.status)
            from TroubleTicket ticket
            where ticket.tenantId = :tenantId
            order by ticket.createdAt, ticket.id
            """)
    List<TroubleTicketSummary> findSummariesByTenant(String tenantId);
}
