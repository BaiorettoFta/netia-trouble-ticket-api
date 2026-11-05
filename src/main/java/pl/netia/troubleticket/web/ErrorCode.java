package pl.netia.troubleticket.web;

import org.springframework.http.HttpStatus;
import pl.netia.troubleticket.openapi.model.ErrorDto;

/**
 * Error codes returned in the {@code code} field of the contract {@code Error} schema, with their HTTP status
 * and default message. Codes and messages shown in the contract examples are used verbatim.
 */
public enum ErrorCode {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Brak poprawnego Bearer tokenu."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Użytkownik nie ma uprawnień do wykonania tej operacji.");

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
}
