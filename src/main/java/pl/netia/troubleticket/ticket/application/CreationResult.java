package pl.netia.troubleticket.ticket.application;

/**
 * Outcome of the idempotent create operation: a new ticket (201) or the existing one with the same external id
 * (200).
 */
public sealed interface CreationResult {

    TroubleTicketView ticket();

    record Created(TroubleTicketView ticket) implements CreationResult {
    }

    record AlreadyExists(TroubleTicketView ticket) implements CreationResult {
    }
}
