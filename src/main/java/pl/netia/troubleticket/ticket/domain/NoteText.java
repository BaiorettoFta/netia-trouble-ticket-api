package pl.netia.troubleticket.ticket.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Text of a note to add, already validated against the contract schema (required, not empty). Adds the rule the
 * storage needs: no characters PostgreSQL cannot store. Text made only of whitespace is accepted, because the
 * contract allows it.
 */
public record NoteText(String value) {

    public NoteText {
        Objects.requireNonNull(value, "value");
        List<InputViolation> violations = new ArrayList<>();
        TextRules.checkStorable("text", value, violations);
        if (!violations.isEmpty()) {
            throw new InvalidInputException(violations);
        }
    }
}
