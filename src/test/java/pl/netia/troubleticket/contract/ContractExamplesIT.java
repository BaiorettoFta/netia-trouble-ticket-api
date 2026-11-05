package pl.netia.troubleticket.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import pl.netia.troubleticket.openapi.model.NoteDto;
import pl.netia.troubleticket.openapi.model.TroubleTicketDto;
import pl.netia.troubleticket.support.ContractDocument;
import pl.netia.troubleticket.support.IntegrationTest;

/**
 * The request examples of the contract work as they are: a client that copies them from the specification gets
 * the documented answers from all five operations. Every response is also checked against the contract schema.
 */
class ContractExamplesIT extends IntegrationTest {

    @Test
    void requestExamplesOfTheContractWorkAsTheyAre() {
        Map<String, Object> createExample = ContractDocument.example("TroubleTicketCreateRequest");
        long serviceId = ((Number) createExample.get("serviceId")).longValue();
        jdbc.sql("insert into service_instance (id, tenant_id, status) values (:id, :tenantId, 'active')")
                .param("id", serviceId)
                .param("tenantId", tenant)
                .update();
        String createRequest = ContractDocument.exampleJson("TroubleTicketCreateRequest");

        TroubleTicketDto created = body(send(client.post().uri("/api/v1/troubleTicket"), createRequest)
                .expectStatus().isCreated()
                .expectBody(TroubleTicketDto.class));
        assertThat(created.externalId()).isEqualTo(createExample.get("externalId"));
        assertThat(created.serviceId()).isEqualTo(serviceId);
        assertThat(created.description()).isEqualTo(createExample.get("description"));
        assertThat(created.notes()).singleElement().extracting(NoteDto::text).isEqualTo(createExample.get("note"));

        TroubleTicketDto createdAgain = body(send(client.post().uri("/api/v1/troubleTicket"), createRequest)
                .expectStatus().isOk()
                .expectBody(TroubleTicketDto.class));
        assertThat(createdAgain.id()).isEqualTo(created.id());

        NoteDto note = body(send(client.post().uri("/api/v1/troubleTicket/{id}/note", created.id()),
                ContractDocument.exampleJson("NoteCreateRequest"))
                .expectStatus().isCreated()
                .expectBody(NoteDto.class));
        assertThat(note.text()).isEqualTo(ContractDocument.example("NoteCreateRequest").get("text"));

        TroubleTicketDto closed = body(send(client.patch().uri("/api/v1/troubleTicket/{id}", created.id()),
                ContractDocument.exampleJson("TroubleTicketCloseRequest"))
                .expectStatus().isOk()
                .expectBody(TroubleTicketDto.class));
        assertThat(closed.status().getValue()).isEqualTo("closed");
        assertThat(closed.notes()).extracting(NoteDto::id).containsExactly(created.notes().getFirst().id(), note.id());

        client.get().uri("/api/v1/troubleTicket")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].externalId").isEqualTo(createExample.get("externalId"))
                .jsonPath("$[0].status").isEqualTo("closed");

        TroubleTicketDto fetched = body(client.get().uri("/api/v1/troubleTicket/{id}", created.id())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody(TroubleTicketDto.class));
        assertThat(fetched).isEqualTo(closed);
    }

    private RestTestClient.ResponseSpec send(RestTestClient.RequestBodySpec request, String body) {
        return request.header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private static <T> T body(RestTestClient.BodySpec<T, ?> spec) {
        return Objects.requireNonNull(spec.returnResult().getResponseBody());
    }
}
