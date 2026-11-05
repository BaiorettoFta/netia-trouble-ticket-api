package pl.netia.troubleticket.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import pl.netia.troubleticket.openapi.model.TroubleTicketDto;
import pl.netia.troubleticket.support.IntegrationTest;
import pl.netia.troubleticket.support.TestJwtIssuer;

class CreateTroubleTicketIT extends IntegrationTest {

    private static final String SERVICE_NOT_FOUND_MESSAGE =
            "Wskazana usługa nie istnieje, nie jest aktywna albo nie należy do tenant scope użytkownika.";

    @Test
    void createsTicketInStatusNewWithTheRequestNoteAsFirstNote() {
        long serviceId = givenActiveService();
        String year = String.valueOf(Year.now(ZoneOffset.UTC).getValue());

        create(createRequest("OK-1", serviceId), bearer())
                .expectStatus().isCreated()
                .expectHeader().value(HttpHeaders.LOCATION, location ->
                        assertThat(location).matches("/api/v1/troubleTicket/TT-" + year + "-\\d{6,}"))
                .expectBody()
                .jsonPath("$.id").value(String.class, id -> assertThat(id).matches("TT-" + year + "-\\d{6,}"))
                .jsonPath("$.externalId").isEqualTo("OK-1")
                .jsonPath("$.serviceId").isEqualTo(serviceId)
                .jsonPath("$.description").isEqualTo("Brak transmisji danych dla usługi klienta.")
                .jsonPath("$.status").isEqualTo("new")
                .jsonPath("$.notes.length()").isEqualTo(1)
                .jsonPath("$.notes[0].id").value(String.class, id -> assertThat(id).matches("NOTE-\\d{6,}"))
                .jsonPath("$.notes[0].text").isEqualTo("Zgłoszenie utworzone przez konto API partnera.")
                .jsonPath("$.notes[0].date").value(String.class, date ->
                        assertThat(date).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?Z"));
    }

    @Test
    void returnsTheExistingTicketInItsCurrentStateWhenCreatedAgain() {
        long serviceId = givenActiveService();
        String id = givenTicket("OK-2", serviceId);
        givenStatusSetBySoz(id, "inProgress");

        create(createRequest("OK-2", serviceId), bearer())
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.LOCATION, "/api/v1/troubleTicket/" + id)
                .expectBody()
                .jsonPath("$.id").isEqualTo(id)
                .jsonPath("$.status").isEqualTo("inProgress");

        assertThat(ticketCount("OK-2")).isEqualTo(1);
    }

    @Test
    void ignoresTheRestOfTheBodyWhenTheExternalIdIsKnown() {
        long serviceId = givenActiveService();
        String id = givenTicket("OK-3", serviceId);

        create(createRequest("OK-3", serviceId).replace("Brak transmisji", "Inny opis, brak transmisji"), bearer())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id)
                .jsonPath("$.description").isEqualTo("Brak transmisji danych dla usługi klienta.");
    }

    @Test
    void sameExternalIdInAnotherTenantIsAnotherTicket() {
        String id = givenTicket("OK-4", givenActiveService());
        String otherTenant = tenant + "-other";
        long otherService = givenService(otherTenant, "active");

        create(createRequest("OK-4", otherService), TestJwtIssuer.token().tenant(otherTenant).asBearer())
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.id").value(String.class, otherId -> assertThat(otherId).isNotEqualTo(id));
    }

    @ParameterizedTest(name = "service {0}")
    @ValueSource(strings = {"missing", "inactive", "of another tenant"})
    void rejectsServiceThatIsMissingInactiveOrForeign(String kind) {
        long serviceId = switch (kind) {
            case "missing" -> 9_999_999_999_999L;
            case "inactive" -> givenService(tenant, "inactive");
            default -> givenService(tenant + "-foreign", "active");
        };

        create(createRequest("OK-5", serviceId), bearer())
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("SERVICE_NOT_FOUND")
                .jsonPath("$.message").isEqualTo(SERVICE_NOT_FOUND_MESSAGE);

        assertThat(ticketCount("OK-5")).isZero();
    }

    @Test
    void retryReturnsTheTicketEvenAfterTheServiceWasDeactivated() {
        long serviceId = givenActiveService();
        String id = givenTicket("OK-6", serviceId);
        jdbc.sql("update service_instance set status = 'inactive' where id = :id").param("id", serviceId).update();

        create(createRequest("OK-6", serviceId), bearer())
                .expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo(id);
    }

    @Test
    void acceptsWhitespaceOnlyTextBecauseTheContractAllowsIt() {
        create(createRequest("OK-7", givenActiveService()).replace(
                "Brak transmisji danych dla usługi klienta.", "   "), bearer())
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.description").isEqualTo("   ");
    }

    @Test
    void rejectsTextTheDatabaseCannotStoreInsteadOfFailing() {
        String nulCharacter = "\\" + "u0000";

        create(createRequest("OK-8", givenActiveService()).replace("Brak transmisji", "Brak" + nulCharacter),
                bearer())
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo("Pole description zawiera niedozwolone znaki.");
    }

    @Test
    void rejectsCreateWithoutTheWriteScope() {
        create(createRequest("OK-9", givenActiveService()),
                TestJwtIssuer.token().tenant(tenant).scopes("trouble-ticket:read").asBearer())
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void concurrentCreatesWithOneExternalIdYieldExactlyOneTicket() throws Exception {
        String body = createRequest("OK-RACE", givenActiveService());
        String bearer = bearer();
        int requests = 20;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<EntityExchangeResult<TroubleTicketDto>>> responses = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < requests; i++) {
                responses.add(executor.submit(() -> {
                    start.await();
                    return create(body, bearer).expectBody(TroubleTicketDto.class).returnResult();
                }));
            }
            start.countDown();
        }

        List<Integer> statuses = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Future<EntityExchangeResult<TroubleTicketDto>> response : responses) {
            EntityExchangeResult<TroubleTicketDto> result = response.get();
            statuses.add(result.getStatus().value());
            ids.add(Objects.requireNonNull(result.getResponseBody()).id());
        }
        assertThat(statuses).containsOnlyOnce(201).containsOnly(201, 200).hasSize(requests);
        assertThat(ids).hasSize(1);
        assertThat(ticketCount("OK-RACE")).isEqualTo(1);
    }

    private RestTestClient.ResponseSpec create(String body, String bearer) {
        return client.post().uri("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private long ticketCount(String externalId) {
        return jdbc.sql("select count(*) from trouble_ticket where tenant_id = :tenantId and external_id = :externalId")
                .param("tenantId", tenant)
                .param("externalId", externalId)
                .query(Long.class)
                .single();
    }
}
