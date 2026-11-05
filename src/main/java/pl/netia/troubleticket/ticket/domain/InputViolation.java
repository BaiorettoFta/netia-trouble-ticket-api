package pl.netia.troubleticket.ticket.domain;

/**
 * A rule the input breaks beyond the contract schema: a value the database cannot store or a key that is too long
 * for its unique index.
 *
 * @param field name of the field as in the API contract
 * @param limit maximum length for {@link Reason#TOO_LONG}, otherwise 0
 */
public record InputViolation(String field, Reason reason, int limit) {

    public enum Reason {
        FORBIDDEN_CHARACTERS,
        TOO_LONG
    }

    static InputViolation forbiddenCharacters(String field) {
        return new InputViolation(field, Reason.FORBIDDEN_CHARACTERS, 0);
    }

    static InputViolation tooLong(String field, int limit) {
        return new InputViolation(field, Reason.TOO_LONG, limit);
    }
}
