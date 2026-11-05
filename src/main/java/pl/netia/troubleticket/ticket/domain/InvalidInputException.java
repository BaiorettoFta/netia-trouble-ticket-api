package pl.netia.troubleticket.ticket.domain;

import java.util.List;

/**
 * Input passed the contract schema but cannot be accepted; carries every violation at once.
 */
public class InvalidInputException extends RuntimeException {

    private final List<InputViolation> violations;

    public InvalidInputException(List<InputViolation> violations) {
        super("Invalid input: " + violations);
        this.violations = List.copyOf(violations);
    }

    public List<InputViolation> violations() {
        return violations;
    }
}
