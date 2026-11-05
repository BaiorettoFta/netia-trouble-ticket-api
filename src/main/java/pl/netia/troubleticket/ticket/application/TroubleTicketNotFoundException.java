package pl.netia.troubleticket.ticket.application;

/**
 * The ticket does not exist or belongs to another tenant; the caller is not told which.
 */
public class TroubleTicketNotFoundException extends RuntimeException {

    public TroubleTicketNotFoundException(String id) {
        super("Trouble ticket " + id + " does not exist or belongs to another tenant");
    }
}
