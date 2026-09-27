package app.meethalfway.adapters.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The single, consistent error envelope returned by every endpoint on failure
 * (Requirements 11.2, 11.3). It is deliberately minimal and machine-readable so
 * the frontend can react to it uniformly, and it never leaks stack traces,
 * internal messages, or provider secrets.
 *
 * <p>{@code fieldErrors} is present only for validation failures and is omitted
 * from the JSON when empty/null. JSON fields are camelCase.
 *
 * @param code        a stable, machine-readable error code (e.g.
 *                    {@code VALIDATION_ERROR}, {@code NOT_FOUND},
 *                    {@code ROUTING_FAILURE}, {@code RATE_LIMITED})
 * @param message     a human-meaningful, actionable top-level message
 * @param fieldErrors per-field problems for validation errors; {@code null}/empty
 *                    otherwise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        String code,
        String message,
        List<FieldErrorResponse> fieldErrors) {

    /**
     * Builds an envelope with no field-level detail.
     *
     * @param code    the machine-readable error code
     * @param message the actionable top-level message
     * @return an {@link ErrorResponse} whose {@code fieldErrors} is {@code null}
     */
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null);
    }
}
