package app.meethalfway.adapters.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for creating a meeting (Requirements 1, 9.1, 11.2).
 *
 * <p>Boundary invariants enforced with Jakarta Bean Validation so a malformed
 * request yields a consistent 400 before any use case runs:
 * <ul>
 *   <li>{@code participants} is present and holds 2&ndash;10 entries
 *       (Requirement 1); each entry is cascaded ({@link Valid}) so coordinate
 *       ranges are checked too;</li>
 *   <li>{@code transportMode} is present; its supported-value check
 *       ({@code driving}/{@code walking}) is performed when mapping through
 *       {@code TransportMode.fromValue}, which yields a 400 naming the supported
 *       modes for any unsupported value.</li>
 * </ul>
 *
 * <p>JSON fields are camelCase.
 *
 * @param participants  the participants (2&ndash;10)
 * @param transportMode the shared transport mode wire value ({@code driving} or
 *                      {@code walking})
 */
public record CreateMeetingRequest(
        @NotNull(message = "participants must not be null")
        @Size(min = 2, max = 10, message = "a meeting requires between 2 and 10 participants")
        @Valid
        List<ParticipantRequest> participants,

        @NotNull(message = "transportMode must not be null")
        String transportMode) {
}
