package app.meethalfway.meetings.domain.model;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.shared.domain.ParticipantId;

/**
 * A single participant location that could not be routed, together with the
 * specific reason why (Requirement 6.1).
 *
 * <p>The engine collects one {@code RoutingError} per affected participant and
 * surfaces them through {@link RecommendationOutcome.RoutingFailure}. The
 * {@code reason} is a human-meaningful, actionable message the web adapter can
 * relay to the Creator so they can correct or remove the location (Requirement
 * 6.2). No recommendation is produced while any routing error exists, so a
 * participant is never silently omitted (Requirement 6.3, 6.4).
 *
 * @param participantId the participant whose location could not be routed
 * @param location      the coordinate that failed to route, echoed back so the
 *                      Creator can identify exactly which point to fix
 * @param reason        the specific, non-blank reason the route could not be
 *                      computed (e.g. timeout, unreachable, out of coverage)
 */
public record RoutingError(
        ParticipantId participantId,
        Coordinate location,
        String reason) {

    public RoutingError {
        if (participantId == null) {
            throw new IllegalArgumentException("participantId must not be null");
        }
        if (location == null) {
            throw new IllegalArgumentException("location must not be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must be a non-blank explanation");
        }
    }
}
