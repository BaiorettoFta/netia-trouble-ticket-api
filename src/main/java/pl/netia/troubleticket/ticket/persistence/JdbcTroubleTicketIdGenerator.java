package pl.netia.troubleticket.ticket.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import pl.netia.troubleticket.ticket.domain.TroubleTicketIdGenerator;

/**
 * Ids in the format of the contract examples, {@code TT-<year>-<number>}: easy to read out over the phone. Numbers
 * come from a database sequence, so they are unique across instances of the service; they run on across years and
 * are never cut, numbers above 999999 simply get more digits. The year is the UTC year of creation.
 */
@Component
class JdbcTroubleTicketIdGenerator implements TroubleTicketIdGenerator {

    private final JdbcClient jdbc;

    JdbcTroubleTicketIdGenerator(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String nextId(Instant createdAt) {
        long number = jdbc.sql("select nextval('trouble_ticket_number_seq')").query(Long.class).single();
        return format(createdAt, number);
    }

    static String format(Instant createdAt, long number) {
        return "TT-%d-%06d".formatted(createdAt.atZone(ZoneOffset.UTC).getYear(), number);
    }
}
