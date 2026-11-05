package pl.netia.troubleticket.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import org.junit.jupiter.api.Test;

/**
 * The contract check in the integration tests is only useful if it really fails on a broken response.
 */
class ContractValidatorTest {

    private static final SimpleRequest GET_TICKET = SimpleRequest.Builder
            .get("/api/v1/troubleTicket/TT-2026-000001")
            .withAuthorization("Bearer token")
            .build();

    private static final String VALID_TICKET = """
            {"id":"TT-2026-000001","externalId":"OK-123456","serviceId":987654321,"description":"Opis",
             "status":"new","notes":[{"id":"NOTE-000001","text":"Notatka","date":"2026-03-19T10:15:00Z"}]}
            """;

    @Test
    void acceptsResponseThatMatchesTheContract() {
        assertThat(ContractValidator.violations(GET_TICKET, json(200, VALID_TICKET))).isEmpty();
    }

    @Test
    void detectsFieldTheContractDoesNotAllow() {
        String withTenant = VALID_TICKET.replace("\"status\"", "\"tenantId\":\"tenant-a\",\"status\"");

        assertThat(ContractValidator.violations(GET_TICKET, json(200, withTenant))).contains("tenantId");
    }

    @Test
    void detectsMissingRequiredField() {
        String withoutNotes = VALID_TICKET.replaceAll(",\"notes\":\\[.*]", "");

        assertThat(ContractValidator.violations(GET_TICKET, json(200, withoutNotes))).contains("notes");
    }

    @Test
    void detectsStatusValueOutsideTheEnum() {
        String unknownStatus = VALID_TICKET.replace("\"new\"", "\"cancelled\"");

        assertThat(ContractValidator.violations(GET_TICKET, json(200, unknownStatus))).contains("enumeration");
    }

    @Test
    void detectsUndocumentedStatusCode() {
        assertThat(ContractValidator.violations(GET_TICKET, json(409, "{}"))).isNotEmpty();
    }

    private static SimpleResponse json(int status, String body) {
        return SimpleResponse.Builder.status(status).withContentType("application/json").withBody(body).build();
    }
}
