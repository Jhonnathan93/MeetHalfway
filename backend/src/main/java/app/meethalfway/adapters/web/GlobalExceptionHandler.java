package app.meethalfway.adapters.web;

import app.meethalfway.adapters.web.dto.ErrorResponse;
import app.meethalfway.adapters.web.dto.FieldErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized, consistent error mapping for the REST surface (Requirements
 * 11.2, 11.3). Every failure is rendered through the single {@link ErrorResponse}
 * envelope with a stable machine-readable {@code code}, an actionable
 * {@code message}, and (for validation failures) per-field detail.
 *
 * <p>Crucially, no stack trace, internal exception message that could leak
 * secrets, or provider key is ever written to the body. Bean-validation and
 * domain-invariant failures map to 400; a missing meeting maps to 404.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles request-body validation failures (Bean Validation on
     * {@code @Valid @RequestBody}). Produces a 400 with one field error per
     * rejected value.
     *
     * @param exception the validation exception raised by Spring MVC
     * @return a 400 response with a consistent validation envelope
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception) {
        List<FieldErrorResponse> fieldErrors = new ArrayList<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.add(new FieldErrorResponse(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        ErrorResponse body = new ErrorResponse(
                "VALIDATION_ERROR", "One or more fields are invalid.", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Handles constraint violations raised on method parameters (e.g. query
     * params validated with {@code @Validated}). Produces a 400 envelope.
     *
     * @param exception the constraint-violation exception
     * @return a 400 response with a consistent validation envelope
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception) {
        List<FieldErrorResponse> fieldErrors = new ArrayList<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            String path = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
            fieldErrors.add(new FieldErrorResponse(path, violation.getMessage()));
        }
        ErrorResponse body = new ErrorResponse(
                "VALIDATION_ERROR", "One or more parameters are invalid.", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Handles domain-invariant failures surfaced as {@link IllegalArgumentException}
     * (e.g. an unsupported transport mode, an out-of-range coordinate caught by a
     * value object). Produces a 400 carrying the actionable message from the
     * domain, which by design names the offending value/mode without leaking
     * internals.
     *
     * @param exception the domain-invariant failure
     * @return a 400 response with a consistent envelope
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        ErrorResponse body = ErrorResponse.of("VALIDATION_ERROR", safeMessage(exception));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Handles a missing meeting (or other absent resource) surfaced as
     * {@link NoSuchElementException}. Produces a 404 with a stable code.
     *
     * @param exception the not-found signal
     * @return a 404 response with a consistent envelope
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNoSuchElement(NoSuchElementException exception) {
        ErrorResponse body = ErrorResponse.of("NOT_FOUND", "The requested meeting was not found.");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Returns a non-null, non-blank message, falling back to a generic string so
     * a {@code null} message never becomes a misleading response and no internal
     * detail is fabricated.
     */
    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? "The request was invalid." : message;
    }
}
