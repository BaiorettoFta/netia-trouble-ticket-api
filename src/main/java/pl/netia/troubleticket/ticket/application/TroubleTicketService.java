package pl.netia.troubleticket.ticket.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.netia.troubleticket.ticket.application.CreationResult.AlreadyExists;
import pl.netia.troubleticket.ticket.application.CreationResult.Created;
import pl.netia.troubleticket.ticket.domain.NewTroubleTicket;
import pl.netia.troubleticket.ticket.domain.ServiceInventory;
import pl.netia.troubleticket.ticket.domain.TenantId;
import pl.netia.troubleticket.ticket.domain.TroubleTicket;
import pl.netia.troubleticket.ticket.domain.TroubleTicketIdGenerator;
import pl.netia.troubleticket.ticket.persistence.TroubleTicketRepository;

/**
 * Use cases of the public Trouble Ticket API. Every operation works within the caller's tenant only.
 */
@Service
public class TroubleTicketService {

    private final TroubleTicketRepository tickets;
    private final ServiceInventory serviceInventory;
    private final TroubleTicketIdGenerator idGenerator;
    private final Clock clock;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;

    public TroubleTicketService(TroubleTicketRepository tickets, ServiceInventory serviceInventory,
            TroubleTicketIdGenerator idGenerator, Clock clock, PlatformTransactionManager transactionManager) {
        this.tickets = tickets;
        this.serviceInventory = serviceInventory;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    /**
     * Creates a ticket or, when the tenant already has one with the same external id, returns that ticket in its
     * current state, as the contract requires. The existing ticket is looked up before the service is checked, so
     * a retry of a successful request always gets 200, even if the service was deactivated in the meantime.
     */
    public CreationResult create(TenantId tenantId, String actor, NewTroubleTicket newTicket) {
        try {
            return inTransaction(writeTransaction, () -> createUnlessExists(tenantId, actor, newTicket));
        } catch (DataIntegrityViolationException conflict) {
            // The unique index on (tenant_id, external_id) rejected the insert: a concurrent request with the same
            // key committed first. PostgreSQL has aborted this transaction, so the winner is read in a new one.
            Optional<TroubleTicketView> winner =
                    inTransaction(readTransaction, () -> findByExternalId(tenantId, newTicket.externalId()));
            return new AlreadyExists(winner.orElseThrow(() -> conflict));
        }
    }

    private CreationResult createUnlessExists(TenantId tenantId, String actor, NewTroubleTicket newTicket) {
        Optional<TroubleTicketView> existing = findByExternalId(tenantId, newTicket.externalId());
        if (existing.isPresent()) {
            return new AlreadyExists(existing.get());
        }
        if (!serviceInventory.isActiveServiceOf(tenantId, newTicket.serviceId())) {
            throw new ServiceNotFoundException(newTicket.serviceId());
        }
        Instant now = now();
        TroubleTicket ticket = TroubleTicket.open(idGenerator.nextId(now), tenantId, newTicket, actor, now);
        tickets.saveAndFlush(ticket);
        return new Created(TroubleTicketView.of(ticket));
    }

    private Optional<TroubleTicketView> findByExternalId(TenantId tenantId, String externalId) {
        return tickets.findWithNotesByTenantAndExternalId(tenantId.value(), externalId).map(TroubleTicketView::of);
    }

    /**
     * Millisecond precision, so the time returned right after a change equals the time read back later.
     */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static <T> T inTransaction(TransactionTemplate transaction, Supplier<T> work) {
        return Objects.requireNonNull(transaction.execute(status -> work.get()));
    }
}
