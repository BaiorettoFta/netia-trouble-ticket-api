package pl.netia.troubleticket.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.netia.troubleticket.openapi.model.ErrorDto;
import pl.netia.troubleticket.support.IntegrationTest;
import pl.netia.troubleticket.support.TestJwtIssuer;

class CloseTroubleTicketIT extends IntegrationTest {

    private static final String CLOSE_REQUEST = """
            { "status": "closed" }
            """;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void closesANewTicketAndKeepsItsNotes() {
        String id = givenTicket("OK-1", givenActiveService());

        close(id, bearer())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id)
                .jsonPath("$.status").isEqualTo("closed")
                .jsonPath("$.notes.length()").isEqualTo(1)
                .jsonPath("$.notes[0].text").isEqualTo("Zgłoszenie utworzone przez konto API partnera.");

        assertThat(stored(id).status()).isEqualTo("closed");
    }

    @ParameterizedTest(name = "from {0}")
    @ValueSource(strings = {"acknowledged", "inProgress", "resolved"})
    void closesATicketInAnyOpenStatusSetBySoz(String status) {
        String id = givenTicket("OK-2", givenActiveService());
        givenStatusSetBySoz(id, status);

        close(id, bearer())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("closed");
    }

    @Test
    void recordsWhoClosedTheTicket() {
        String id = givenTicket("OK-3", givenActiveService());
        Instant beforeClosing = Instant.now();

        close(id, TestJwtIssuer.token().tenant(tenant).subject("partner-a-operator").asBearer())
                .expectStatus().isOk();

        StoredTicket ticket = stored(id);
        assertThat(ticket.createdBy()).isEqualTo("partner-a");
        assertThat(ticket.updatedBy()).isEqualTo("partner-a-operator");
        assertThat(ticket.updatedAt()).isAfterOrEqualTo(beforeClosing.minusMillis(1));
    }

    @Test
    void closingAgainReturnsTheClosedTicketUnchanged() {
        String id = givenTicket("OK-4", givenActiveService());
        close(id, bearer()).expectStatus().isOk();
        StoredTicket closed = stored(id);

        close(id, TestJwtIssuer.token().tenant(tenant).subject("partner-a-retry").asBearer())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("closed");

        assertThat(stored(id)).isEqualTo(closed);
    }

    @Test
    void rejectedTicketStaysRejected() {
        String id = givenTicket("OK-5", givenActiveService());
        givenStatusSetBySoz(id, "rejected");

        close(id, bearer())
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("STATUS_TRANSITION_NOT_ALLOWED")
                .jsonPath("$.message").isEqualTo("Nie można zmienić statusu zgłoszenia z rejected na closed.");

        assertThat(stored(id).status()).isEqualTo("rejected");
    }

    @Test
    void ticketOfAnotherTenantIsNotFoundAndStaysOpen() {
        String id = givenTicket("OK-6", givenActiveService());

        close(id, TestJwtIssuer.token().tenant(tenant + "-other").asBearer())
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("TROUBLE_TICKET_NOT_FOUND");

        assertThat(stored(id).status()).isEqualTo("new");
    }

    @Test
    void unknownTicketIsNotFound() {
        close("TT-2026-999999", bearer())
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("TROUBLE_TICKET_NOT_FOUND");
    }

    @Test
    void createdAgainAfterClosingTheTicketIsReturnedAsClosed() {
        long serviceId = givenActiveService();
        String id = givenTicket("OK-7", serviceId);
        close(id, bearer()).expectStatus().isOk();

        client.post().uri("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(createRequest("OK-7", serviceId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id)
                .jsonPath("$.status").isEqualTo("closed");
    }

    /**
     * The ticket handling system holds the ticket row while it rejects the ticket. A close request arriving at that
     * moment must wait for the commit and then judge the committed status, instead of overwriting the rejection
     * with a status it read earlier.
     */
    @Test
    void closeWaitsForAConcurrentStatusChangeAndJudgesTheCommittedStatus() throws Exception {
        String id = givenTicket("OK-8", givenActiveService());
        String bearer = bearer();
        TransactionTemplate soz = new TransactionTemplate(transactionManager);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<EntityExchangeResult<ErrorDto>> closing = Objects.requireNonNull(soz.execute(transaction -> {
                jdbc.sql("select id from trouble_ticket where id = :id for update")
                        .param("id", id)
                        .query(String.class)
                        .single();
                Future<EntityExchangeResult<ErrorDto>> request =
                        executor.submit(() -> close(id, bearer).expectBody(ErrorDto.class).returnResult());
                awaitSessionWaitingForLock();
                givenStatusSetBySoz(id, "rejected");
                return request;
            }));

            EntityExchangeResult<ErrorDto> result = closing.get(30, TimeUnit.SECONDS);
            assertThat(result.getStatus().value()).isEqualTo(400);
            assertThat(Objects.requireNonNull(result.getResponseBody()).code())
                    .isEqualTo("STATUS_TRANSITION_NOT_ALLOWED");
        }
        assertThat(stored(id).status()).isEqualTo("rejected");
    }

    private RestTestClient.ResponseSpec close(String id, String bearer) {
        return client.patch().uri("/api/v1/troubleTicket/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(CLOSE_REQUEST)
                .exchange();
    }

    /**
     * Polls PostgreSQL until another session waits for a lock, so the test does not depend on timing.
     */
    private void awaitSessionWaitingForLock() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            boolean waiting = jdbc.sql("""
                            select exists (
                                select 1 from pg_stat_activity
                                where datname = current_database() and wait_event_type = 'Lock')
                            """)
                    .query(Boolean.class)
                    .single();
            if (waiting) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
        throw new AssertionError("No session started waiting for the locked ticket row");
    }

    private StoredTicket stored(String id) {
        return jdbc.sql("select status, created_by, updated_by, updated_at from trouble_ticket where id = :id")
                .param("id", id)
                .query((row, number) -> new StoredTicket(row.getString("status"), row.getString("created_by"),
                        row.getString("updated_by"), row.getObject("updated_at", OffsetDateTime.class).toInstant()))
                .single();
    }

    private record StoredTicket(String status, String createdBy, String updatedBy, Instant updatedAt) {
    }
}
