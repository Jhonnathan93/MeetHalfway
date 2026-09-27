package app.meethalfway.adapters.web.dto;

/**
 * A single field-level validation problem within an {@link ErrorResponse}.
 *
 * <p>Identifies the offending request field (or participant path) and a
 * human-meaningful, actionable message. Never carries stack traces or secrets.
 *
 * @param field   the offending field or path (e.g. {@code participants[0].lat})
 * @param message the actionable explanation of what is wrong
 */
public record FieldErrorResponse(String field, String message) {
}
