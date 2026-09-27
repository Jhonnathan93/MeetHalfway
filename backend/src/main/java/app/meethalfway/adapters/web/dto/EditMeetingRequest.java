package app.meethalfway.adapters.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for editing an existing meeting (Requirements 9.2, 9.4, 11.2).
 *
 * <p>Shares the shape and boundary invariants of {@link CreateMeetingRequest}:
 * the Creator re-enters the full participant set (2&ndash;10) and the shared
 * transport mode. Validation runs at the boundary; the supported-mode check is
 * performed via {@code TransportMode.fromValue} during mapping.
 *
 * <p>JSON fields are camelCase.
 *
 * @param participants  the replacement participants (2&ndash;10)
 * @param transportMode the replacement transport mode wire value
 *                      ({@code driving} or {@code walking})
 */
public record EditMeetingRequest(
        @NotNull(message = "participants must not be null")
        @Size(min = 2, max = 10, message = "a meeting requires between 2 and 10 participants")
        @Valid
        List<ParticipantRequest> participants,

        @NotNull(message = "transportMode must not be null")
        String transportMode) {
}
