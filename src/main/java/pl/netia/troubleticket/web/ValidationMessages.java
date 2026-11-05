package pl.netia.troubleticket.web;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.validation.FieldError;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Turns validation and parsing failures into one readable message. The contract {@code Error} schema has no field
 * for a list of violations, so all violations go into {@code message}, ordered by field name so that the same
 * request always gets the same message.
 */
final class ValidationMessages {

    static final String NOT_JSON = "Treść żądania nie jest poprawnym dokumentem JSON.";

    private ValidationMessages() {
    }

    static String forFieldErrors(Collection<FieldError> errors) {
        return errors.stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(ValidationMessages::forFieldError)
                .distinct()
                .collect(Collectors.joining(" "));
    }

    /**
     * Message for a body Jackson could not bind. Only the field name is revealed, never parser internals.
     */
    static String forUnreadableBody(JacksonException exception) {
        if (exception instanceof UnrecognizedPropertyException unknown) {
            return "Nieznane pole " + unknown.getPropertyName() + ".";
        }
        String field = fieldPath(exception);
        if (field.isEmpty()) {
            return NOT_JSON;
        }
        if (exception instanceof MismatchedInputException mismatch
                && mismatch.getTargetType() != null && mismatch.getTargetType().isEnum()) {
            return "Pole " + field + " ma niedozwoloną wartość dla tej operacji.";
        }
        return "Pole " + field + " ma niepoprawny typ.";
    }

    private static String forFieldError(FieldError error) {
        String field = error.getField();
        // Constraints come from the contract schema: required, minLength: 1 and minimum: 1.
        return switch (Objects.requireNonNullElse(error.getCode(), "")) {
            case "NotNull" -> "Pole " + field + " jest wymagane.";
            case "Size" -> "Pole " + field + " nie może być puste.";
            case "Min" -> "Pole " + field + " musi być liczbą całkowitą nie mniejszą niż 1.";
            default -> "Pole " + field + " ma niepoprawną wartość.";
        };
    }

    private static String fieldPath(JacksonException exception) {
        return exception.getPath().stream()
                .map(JacksonException.Reference::getPropertyName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("."));
    }
}
