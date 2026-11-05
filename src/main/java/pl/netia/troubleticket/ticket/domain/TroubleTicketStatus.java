package pl.netia.troubleticket.ticket.domain;

/**
 * Lifecycle of a trouble ticket. The public API may only create {@code new} tickets and close them; the other
 * statuses are set by the ticket handling system (SOZ), which is outside the scope of this service.
 */
public enum TroubleTicketStatus {

    NEW("new"),
    ACKNOWLEDGED("acknowledged"),
    IN_PROGRESS("inProgress"),
    RESOLVED("resolved"),
    CLOSED("closed"),
    REJECTED("rejected");

    private final String code;

    TroubleTicketStatus(String code) {
        this.code = code;
    }

    /**
     * The value used by the API contract and stored in the database, so one vocabulary serves both and renaming a
     * Java constant cannot break stored data.
     */
    public String code() {
        return code;
    }

    public static TroubleTicketStatus fromCode(String code) {
        for (TroubleTicketStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown trouble ticket status: " + code);
    }
}
