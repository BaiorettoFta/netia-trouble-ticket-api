package pl.netia.troubleticket.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import pl.netia.troubleticket.openapi.model.ErrorDto;

/**
 * Error codes returned in the {@code code} field of the contract {@code Error} schema, with their HTTP status
 * and default message. Codes and messages shown in the contract examples are used verbatim; the remaining ones
 * cover protocol-level situations the contract does not describe.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Żądanie jest niepoprawne."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Brak poprawnego Bearer tokenu."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Użytkownik nie ma uprawnień do wykonania tej operacji."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Zasób nie istnieje."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Metoda HTTP nie jest obsługiwana dla tego zasobu."),
    CONTENT_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Treść żądania jest zbyt duża (limit 1 MB)."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Nieobsługiwany typ treści. Wymagany jest application/json."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Wystąpił nieoczekiwany błąd.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public ErrorDto toBody(String message, String requestId) {
        return new ErrorDto(name(), message, requestId);
    }

    /**
     * Code for a status produced by the framework rather than by the application.
     */
    public static ErrorCode forStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 413 -> CONTENT_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status.is4xxClientError() ? VALIDATION_ERROR : INTERNAL_ERROR;
        };
    }
}
