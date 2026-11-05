package pl.netia.troubleticket.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;
import pl.netia.troubleticket.support.ApiSliceTest;
import pl.netia.troubleticket.support.FakeJwtDecoder;
import pl.netia.troubleticket.ticket.application.TroubleTicketService;

@ApiSliceTest
class AuthorizationTest {

    @MockitoBean
    private TroubleTicketService troubleTicketService;

    @Autowired
    private MockMvc mockMvc;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindTo(mockMvc).build();
    }

    static Stream<Arguments> operations() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/v1/troubleTicket", "read"),
                Arguments.of(HttpMethod.GET, "/api/v1/troubleTicket/TT-2026-000001", "read"),
                Arguments.of(HttpMethod.POST, "/api/v1/troubleTicket", "write"),
                Arguments.of(HttpMethod.PATCH, "/api/v1/troubleTicket/TT-2026-000001", "write"),
                Arguments.of(HttpMethod.POST, "/api/v1/troubleTicket/TT-2026-000001/note", "write"));
    }

    @ParameterizedTest(name = "{0} {1} without the {2} scope")
    @MethodSource("operations")
    void rejectsTokenWithoutRequiredScopeBeforeReadingTheBody(HttpMethod method, String path, String requiredScope) {
        String otherScope = requiredScope.equals("read") ? "write" : "read";

        client.method(method).uri(path)
                .header(HttpHeaders.AUTHORIZATION, FakeJwtDecoder.bearer("tenant-a", otherScope))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{ this is not even JSON")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().value(HttpHeaders.WWW_AUTHENTICATE,
                        value -> assertThat(value).contains("error=\"insufficient_scope\""))
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN")
                .jsonPath("$.message").isEqualTo("Użytkownik nie ma uprawnień do wykonania tej operacji.")
                .jsonPath("$.requestId").isNotEmpty();
    }

    @ParameterizedTest(name = "{0} {1} without any scope")
    @MethodSource("operations")
    void rejectsTokenWithoutAnyScope(HttpMethod method, String path, String requiredScope) {
        client.method(method).uri(path)
                .header(HttpHeaders.AUTHORIZATION, FakeJwtDecoder.bearer("tenant-a"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @ParameterizedTest(name = "{0} {1} with the {2} scope")
    @MethodSource("operations")
    void letsTokenWithRequiredScopeThrough(HttpMethod method, String path, String requiredScope) {
        client.method(method).uri(path)
                .header(HttpHeaders.AUTHORIZATION, FakeJwtDecoder.bearer("tenant-a", requiredScope))
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotIn(401, 403));
    }
}
