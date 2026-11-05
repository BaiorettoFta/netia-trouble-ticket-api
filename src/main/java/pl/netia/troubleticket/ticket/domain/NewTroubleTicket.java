package pl.netia.troubleticket.ticket.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Data of a ticket to create, already validated against the contract schema (required fields, non-empty texts,
 * {@code serviceId >= 1}). Adds the rules the storage needs: {@code externalId} is part of a unique index, so its
 * length is limited, and no text may contain characters PostgreSQL cannot store. Text made only of whitespace is
 * accepted, because the contract allows it.
 */
public record NewTroubleTicket(String externalId, long serviceId, String description, String note) {

    public static final int EXTERNAL_ID_MAX_LENGTH = 255;

    public NewTroubleTicket {
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(note, "note");
        List<InputViolation> violations = new ArrayList<>();
        TextRules.checkMaxLength("externalId", externalId, EXTERNAL_ID_MAX_LENGTH, violations);
        TextRules.checkStorable("externalId", externalId, violations);
        TextRules.checkStorable("description", description, violations);
        TextRules.checkStorable("note", note, violations);
        if (!violations.isEmpty()) {
            throw new InvalidInputException(violations);
        }
    }
}
