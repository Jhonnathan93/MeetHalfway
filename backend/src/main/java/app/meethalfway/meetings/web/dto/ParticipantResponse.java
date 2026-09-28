package app.meethalfway.meetings.web.dto;

import app.meethalfway.shared.web.dto.CoordinateResponse;

/**
 * Wire representation of a stored participant in a meeting response.
 *
 * <p>Carries the stable participant id, the (already sanitized) display name,
 * and the starting location. JSON fields are camelCase.
 *
 * @param id       the stable participant identifier
 * @param name     the display name (may be empty)
 * @param location the participant's starting coordinate
 */
public record ParticipantResponse(String id, String name, CoordinateResponse location) {
}
