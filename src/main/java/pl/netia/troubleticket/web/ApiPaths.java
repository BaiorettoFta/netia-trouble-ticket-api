package pl.netia.troubleticket.web;

import java.net.URI;

/**
 * Public paths of the API version 1. The base path comes from the {@code servers} section of the contract.
 */
public final class ApiPaths {

    public static final String BASE = "/api/v1";
    public static final String TROUBLE_TICKETS = BASE + "/troubleTicket";
    public static final String TROUBLE_TICKET = TROUBLE_TICKETS + "/*";
    public static final String TROUBLE_TICKET_NOTES = TROUBLE_TICKET + "/note";

    private ApiPaths() {
    }

    /**
     * Canonical address of a trouble ticket, used in the {@code Location} header. It is relative on purpose:
     * the contract allows a URI reference, and a relative one does not depend on host names behind proxies.
     */
    public static URI troubleTicket(String id) {
        return URI.create(TROUBLE_TICKETS + "/" + id);
    }
}
