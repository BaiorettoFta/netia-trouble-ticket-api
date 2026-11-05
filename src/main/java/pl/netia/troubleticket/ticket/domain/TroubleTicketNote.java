package pl.netia.troubleticket.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A note of a trouble ticket. Notes are never changed or removed. The id comes from a database identity column, so
 * notes of one ticket sorted by id are in the order they were added.
 */
@Entity
@Table(name = "trouble_ticket_note")
public class TroubleTicketNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trouble_ticket_id", nullable = false, updatable = false)
    private TroubleTicket troubleTicket;

    @Column(name = "text", nullable = false, updatable = false, columnDefinition = "text")
    private String text;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TroubleTicketNote() {
        // for JPA
    }

    TroubleTicketNote(TroubleTicket troubleTicket, String text, String createdBy, Instant createdAt) {
        this.troubleTicket = troubleTicket;
        this.text = text;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    /**
     * Database id; {@code null} until the note is stored.
     */
    public Long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
