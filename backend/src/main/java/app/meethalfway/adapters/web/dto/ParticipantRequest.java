package app.meethalfway.adapters.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * Request DTO for a single participant supplied when creating or editing a
 * meeting (Requirements 1, 11.2, 11.3).
 *
 * <p>Boundary validation is expressed with Jakarta Bean Validation so an invalid
 * coordinate is rejected with a consistent 400 before any domain object is
 * built:
 * <ul>
 *   <li>{@code lat} must lie in {@code [-90, 90]};</li>
 *   <li>{@code lng} must lie in {@code [-180, 180]}.</li>
 * </ul>
 * The display {@code name} is optional and is <em>sanitized</em> (not rejected)
 * via {@link NameSanitizer} when the request is mapped to the application layer.
 *
 * <p>JSON fields are camelCase to match the API contract.
 *
 * @param name the optional display name (sanitized before use; may be {@code null})
 * @param lat  latitude in degrees, validated to {@code [-90, 90]}
 * @param lng  longitude in degrees, validated to {@code [-180, 180]}
 */
public record ParticipantRequest(
        String name,

        @DecimalMin(value = "-90.0", message = "lat must be within [-90, 90]")
        @DecimalMax(value = "90.0", message = "lat must be within [-90, 90]")
        double lat,

        @DecimalMin(value = "-180.0", message = "lng must be within [-180, 180]")
        @DecimalMax(value = "180.0", message = "lng must be within [-180, 180]")
        double lng) {
}
