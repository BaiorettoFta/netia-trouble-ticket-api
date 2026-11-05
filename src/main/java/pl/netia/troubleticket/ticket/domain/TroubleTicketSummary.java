package pl.netia.troubleticket.ticket.domain;

/**
 * The fields of a ticket shown in the list; the contract limits the list to exactly these four.
 */
public record TroubleTicketSummary(String externalId, long serviceId, String description,
        TroubleTicketStatus status) {
}
