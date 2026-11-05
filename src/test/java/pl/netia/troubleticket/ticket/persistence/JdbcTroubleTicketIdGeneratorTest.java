package pl.netia.troubleticket.ticket.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class JdbcTroubleTicketIdGeneratorTest {

    @Test
    void formatsIdLikeTheContractExamples() {
        assertThat(JdbcTroubleTicketIdGenerator.format(Instant.parse("2026-03-19T10:15:00Z"), 1))
                .isEqualTo("TT-2026-000001");
    }

    @Test
    void neverCutsNumbersAboveSixDigits() {
        assertThat(JdbcTroubleTicketIdGenerator.format(Instant.parse("2026-03-19T10:15:00Z"), 1_234_567))
                .isEqualTo("TT-2026-1234567");
    }

    @Test
    void takesTheYearInUtc() {
        // Already 2027 in Poland, still 2026 in UTC.
        assertThat(JdbcTroubleTicketIdGenerator.format(Instant.parse("2026-12-31T23:30:00Z"), 42))
                .isEqualTo("TT-2026-000042");
    }
}
