package pl.netia.troubleticket.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;
import pl.netia.troubleticket.openapi.model.NoteDto;
import pl.netia.troubleticket.openapi.model.TroubleTicketDto;
import pl.netia.troubleticket.openapi.model.TroubleTicketStatusDto;
import pl.netia.troubleticket.support.ApiSliceTest;
import pl.netia.troubleticket.support.FakeJwtDecoder;
import tools.jackson.databind.json.JsonMapper;

/**
 * The request schemas use additionalProperties: false and exact JSON types, so the parser must not "fix" input.
 */
@ApiSliceTest(controllers = ProbeController.class)
@Import(ProbeController.class)
class JsonStrictnessTest {

    private static final String VALID_BODY = """
            {
              "externalId": "OK-123456",
              "serviceId": 987654321,
              "description": "Brak transmisji danych dla usługi klienta.",
              "status": "new",
              "note": "Zgłoszenie utworzone przez konto API partnera."
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindTo(mockMvc)
                .defaultHeader(HttpHeaders.AUTHORIZATION, FakeJwtDecoder.bearer("tenant-a", "write"))
                .build();
    }

    @Test
    void acceptsBodyThatMatchesTheSchema() {
        post(VALID_BODY).expectStatus().isNoContent();
    }

    @Test
    void rejectsUnknownField() {
        expectValidationError(VALID_BODY.replace("\"note\"", "\"tenantId\": \"tenant-b\", \"note\""),
                "Nieznane pole tenantId.");
    }

    @ParameterizedTest(name = "serviceId: {0}")
    @CsvSource(delimiter = '|', value = {
            "\"987654321\"",
            "1.5",
            "1.0",
            "true",
            "9223372036854775808",
            "[987654321]"
    })
    void rejectsServiceIdThatIsNotAnInt64Number(String serviceId) {
        expectValidationError(VALID_BODY.replace("987654321", serviceId), "Pole serviceId ma niepoprawny typ.");
    }

    @ParameterizedTest(name = "description: {0}")
    @CsvSource(delimiter = '|', value = {"123", "1.5", "true", "{}", "[\"text\"]"})
    void rejectsDescriptionThatIsNotAString(String description) {
        expectValidationError(
                VALID_BODY.replace("\"Brak transmisji danych dla usługi klienta.\"", description),
                "Pole description ma niepoprawny typ.");
    }

    @ParameterizedTest(name = "status: {0}")
    @CsvSource(delimiter = '|', value = {"\"acknowledged\"", "\"closed\"", "\"NEW\"", "\"New\"", "\"\"", "0"})
    void rejectsStatusOtherThanNew(String status) {
        expectValidationError(VALID_BODY.replace("\"new\"", status),
                "Pole status ma niedozwoloną wartość dla tej operacji.");
    }

    @Test
    void rejectsNullInRequiredField() {
        expectValidationError(VALID_BODY.replace("\"Zgłoszenie utworzone przez konto API partnera.\"", "null"),
                "Pole note jest wymagane.");
    }

    @Test
    void rejectsDuplicateKey() {
        expectValidationError(VALID_BODY.replace("\"note\"", "\"externalId\": \"OK-999\", \"note\""),
                ValidationMessages.NOT_JSON);
    }

    @Test
    void rejectsContentAfterTheJsonDocument() {
        expectValidationError(VALID_BODY + " {}", ValidationMessages.NOT_JSON);
    }

    @Test
    void rejectsArrayInsteadOfObject() {
        expectValidationError("[" + VALID_BODY + "]", ValidationMessages.NOT_JSON);
    }

    @Test
    void rejectsBodyLargerThanOneMegabyte() {
        String hugeDescription = "x".repeat(1_100_000);

        post(VALID_BODY.replace("Brak transmisji danych dla usługi klienta.", hugeDescription))
                .expectStatus().isEqualTo(413)
                .expectBody().jsonPath("$.code").isEqualTo("CONTENT_TOO_LARGE");
    }

    @Test
    void writesResponseFieldsInContractOrder() {
        TroubleTicketDto ticket = new TroubleTicketDto("TT-2026-000001", "OK-123456", 987654321L, "Opis",
                TroubleTicketStatusDto.NEW,
                List.of(new NoteDto("NOTE-000001", "Notatka",
                        OffsetDateTime.of(2026, 3, 19, 10, 15, 0, 0, ZoneOffset.UTC))));

        assertThat(jsonMapper.writeValueAsString(ticket)).isEqualTo("""
                {"id":"TT-2026-000001","externalId":"OK-123456","serviceId":987654321,"description":"Opis",\
                "status":"new","notes":[{"id":"NOTE-000001","text":"Notatka","date":"2026-03-19T10:15:00Z"}]}""");
    }

    private RestTestClient.ResponseSpec post(String body) {
        return client.post().uri(ProbeController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private void expectValidationError(String body, String message) {
        post(body)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.message").isEqualTo(message);
    }
}
