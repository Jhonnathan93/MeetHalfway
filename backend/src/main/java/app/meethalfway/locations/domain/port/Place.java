package app.meethalfway.locations.domain.port;

import app.meethalfway.shared.domain.Coordinate;

/**
 * An establishment near a meeting point, returned by a {@link PlacesProvider}.
 *
 * <p>This is a post-MVP concept: the port and its value types are defined now so
 * the future "list establishments in the zone" increment is a matter of writing a
 * real adapter, not restructuring the core (see design "Ports to the Outside
 * World"). In the MVP no adapter produces {@code Place} instances.
 *
 * <p>Invariant: {@code name} must be present and non-blank, and {@code location}
 * must be a valid {@link Coordinate}.
 *
 * @param name     human-readable establishment name, non-blank
 * @param location geographic point of the establishment
 */
public record Place(String name, Coordinate location) {

    public Place {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("place name must not be null or blank");
        }
        if (location == null) {
            throw new IllegalArgumentException("place location must not be null");
        }
    }
}
