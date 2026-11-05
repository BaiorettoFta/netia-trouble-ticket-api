package pl.netia.troubleticket.web;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamConstraintsException;

/**
 * Renders every error that reaches Spring MVC in the contract {@code Error} format. Clients never see exception
 * class names, SQL or stack traces; unexpected errors are logged with the request id instead.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        log.error("Unexpected error while processing the request", exception);
        return error(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), new HttpHeaders(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ValidationMessages.forFieldErrors(exception.getBindingResult().getFieldErrors());
        return error(ErrorCode.VALIDATION_ERROR, message, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (exception.contains(StreamConstraintsException.class)) {
            return error(ErrorCode.CONTENT_TOO_LARGE, ErrorCode.CONTENT_TOO_LARGE.defaultMessage(), headers, request);
        }
        String message = exception.getCause() instanceof JacksonException jacksonException
                ? ValidationMessages.forUnreadableBody(jacksonException)
                : ValidationMessages.NOT_JSON;
        return error(ErrorCode.VALIDATION_ERROR, message, headers, request);
    }

    /**
     * The client does not accept JSON, so it cannot receive a JSON error body either.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(status).headers(headers).build();
    }

    /**
     * Every other exception handled by Spring MVC (unknown path, unsupported method or media type and so on).
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Request failed with status {}", statusCode.value(), exception);
        }
        ErrorCode code = ErrorCode.forStatus(statusCode);
        return error(code, code.defaultMessage(), headers, request);
    }

    static ResponseEntity<Object> error(ErrorCode code, String message, HttpHeaders headers, WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        return ResponseEntity.status(code.httpStatus())
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(code.toBody(message, RequestIdFilter.requestIdOf(servletRequest)));
    }
}
