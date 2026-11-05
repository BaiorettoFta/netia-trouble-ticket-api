package pl.netia.troubleticket.ticket.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Persistable;

/**
 * A trouble ticket of one tenant: the aggregate root, with its notes inside the aggregate. It is also the JPA entity,
 * a deliberate trade-off: separate domain and persistence models would double the code without benefit at this size.
 * State changes only through its methods; there are no public setters.
 */
@Entity
@Table(name = "trouble_ticket")
public class TroubleTicket implements Persistable<String> {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private String externalId;

    @Column(name = "service_id", nullable = false, updatable = false)
    private long serviceId;

    @Column(name = "description", nullable = false, updatable = false, columnDefinition = "text")
    private String description;

    @Column(name = "status", nullable = false)
    private TroubleTicketStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "troubleTicket", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<TroubleTicketNote> notes = new ArrayList<>();

    /**
     * The id is assigned by the application, so Spring Data cannot tell a new ticket from a stored one by a null id;
     * without this flag {@code save()} would run {@code merge()} with an extra SELECT instead of an INSERT.
     */
    @Transient
    private boolean isNew;

    protected TroubleTicket() {
        // for JPA
    }

    private TroubleTicket(String id, TenantId tenantId, NewTroubleTicket newTicket, String actor, Instant now) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = tenantId.value();
        this.externalId = newTicket.externalId();
        this.serviceId = newTicket.serviceId();
        this.description = newTicket.description();
        this.status = TroubleTicketStatus.NEW;
        this.createdBy = actor;
        this.createdAt = now;
        this.updatedBy = actor;
        this.updatedAt = now;
        this.isNew = true;
    }

    /**
     * Opens a ticket in status {@code new}; the note from the request becomes its first note.
     */
    public static TroubleTicket open(String id, TenantId tenantId, NewTroubleTicket newTicket, String actor,
            Instant now) {
        TroubleTicket ticket = new TroubleTicket(id, tenantId, newTicket, actor, now);
        ticket.notes.add(new TroubleTicketNote(ticket, newTicket.note(), actor, now));
        return ticket;
    }

    /**
     * Closes the ticket at the client's request; the contract lets the client set no other status.
     * <ul>
     *   <li>An open ticket ({@code new}, {@code acknowledged}, {@code inProgress}, {@code resolved}) becomes
     *       {@code closed}, and the closing client is recorded.</li>
     *   <li>A closed ticket stays as it is, so a client that retries after a timeout gets the same answer.</li>
     *   <li>A rejected ticket cannot be closed: rejection is the decision of the ticket handling system, and with no
     *       status history, closing the ticket would erase it.</li>
     * </ul>
     */
    public void close(String actor, Instant now) {
        if (status == TroubleTicketStatus.CLOSED) {
            return;
        }
        if (status == TroubleTicketStatus.REJECTED) {
            throw new StatusTransitionNotAllowedException(id, status, TroubleTicketStatus.CLOSED);
        }
        status = TroubleTicketStatus.CLOSED;
        updatedBy = actor;
        updatedAt = now;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        isNew = false;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public TenantId getTenantId() {
        return new TenantId(tenantId);
    }

    public String getExternalId() {
        return externalId;
    }

    public long getServiceId() {
        return serviceId;
    }

    public String getDescription() {
        return description;
    }

    public TroubleTicketStatus getStatus() {
        return status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Notes in the order they were added; the first one is the note from the create request.
     */
    public List<TroubleTicketNote> getNotes() {
        return Collections.unmodifiableList(notes);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof TroubleTicket ticket && id.equals(ticket.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
