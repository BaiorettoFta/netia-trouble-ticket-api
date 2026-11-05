package pl.netia.troubleticket.ticket.domain;

import java.time.Instant;

/**
 * Port that issues public trouble ticket ids such as {@code TT-2026-000001}.
 */
public interface TroubleTicketIdGenerator {

    String nextId(Instant createdAt);
}
