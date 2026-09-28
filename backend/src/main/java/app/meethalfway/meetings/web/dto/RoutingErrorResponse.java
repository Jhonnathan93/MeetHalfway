package app.meethalfway.meetings.web.dto;

import app.meethalfway.meetings.web.dto.RoutingFailureResponse;
import app.meethalfway.shared.web.dto.CoordinateResponse;

/**
 * Wire representation of a single unroutable participant location within a
 * {@link RoutingFailureResponse} (Requirement 6.1, 6.2).
 *
 * <p>Echoes the participant id, the exact location that failed, and the specific
 * reason so the Creator can identify and correct or remove that point. JSON
 * fields are camelCase.
 *
 * @param participantId the participant whose location could not be routed
 * @param location      the coordinate that failed to route
 * @param reason        the specific, actionable reason the route failed
 */
public record RoutingErrorResponse(
        String participantId,
        CoordinateResponse location,
        String reason) {
}
