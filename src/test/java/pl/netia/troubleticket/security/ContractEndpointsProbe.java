package pl.netia.troubleticket.security;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The five operations of the contract with empty bodies, so access rules are tested on their own: a request that
 * passes security gets 204, without any business logic behind it.
 * Marked as a test component so the full application context of the integration tests does not pick it up;
 * slice tests import it explicitly.
 */
@TestComponent
@RestController
class ContractEndpointsProbe {

    @GetMapping("/api/v1/troubleTicket")
    ResponseEntity<Void> list() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/troubleTicket/{id}")
    ResponseEntity<Void> get() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/troubleTicket")
    ResponseEntity<Void> create() {
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/v1/troubleTicket/{id}")
    ResponseEntity<Void> close() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/troubleTicket/{id}/note")
    ResponseEntity<Void> addNote() {
        return ResponseEntity.noContent().build();
    }
}
