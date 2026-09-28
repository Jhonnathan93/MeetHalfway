package app.meethalfway.meetings.domain.model;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.shared.domain.ParticipantId;

/**
 * A single participant of a meeting: an identity, an optional display name, and
 * the location where they begin travel.
 *
 * <p>Invariants: {@code id} and {@code location} must be present. The name is
 * optional (a participant may be entered without a display name); when absent it
 * is normalized to an empty string so callers never receive {@code null}.
 *
 * @param id       stable participant identifier
 * @param name     display name, optional (never {@code null} after construction)
 * @param location coordinate where this participant begins travel
 */
public record ParticipantInput(ParticipantId id, String name, Coordinate location) {

    public ParticipantInput {
        if (id == null) {
            throw new IllegalArgumentException("participant id must not be null");
        }
        if (location == null) {
            throw new IllegalArgumentException("participant location must not be null");
        }
        name = name == null ? "" : name;
    }
}
