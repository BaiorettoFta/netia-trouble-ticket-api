package pl.netia.troubleticket.ticket.domain;

/**
 * The current status of the ticket does not allow the requested one, for example a rejected ticket cannot be closed.
 */
public class StatusTransitionNotAllowedException extends RuntimeException {

    private final TroubleTicketStatus currentStatus;
    private final TroubleTicketStatus requestedStatus;

    public StatusTransitionNotAllowedException(String ticketId, TroubleTicketStatus currentStatus,
            TroubleTicketStatus requestedStatus) {
        super("Trouble ticket " + ticketId + " cannot change status from " + currentStatus.code() + " to "
                + requestedStatus.code());
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public TroubleTicketStatus currentStatus() {
        return currentStatus;
    }

    public TroubleTicketStatus requestedStatus() {
        return requestedStatus;
    }
}
