package pl.netia.troubleticket.web;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.netia.troubleticket.openapi.model.TroubleTicketCreateRequestDto;

/**
 * Test-only endpoints for checking the shared web behaviour (error format, JSON parsing) independently of the
 * business controllers. The request body is the real DTO generated from the contract.
 */
@RestController
class ProbeController {

    static final String PATH = "/test/probe";

    @PostMapping(path = PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> accept(@Valid @RequestBody TroubleTicketCreateRequestDto request) {
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, String> read() {
        return Map.of("status", "ok");
    }

    @GetMapping(path = PATH + "/failure", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, String> fail() {
        throw new IllegalStateException("Connection to db-internal-01 refused for user app_admin");
    }
}
