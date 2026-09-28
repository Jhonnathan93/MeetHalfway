package app.meethalfway.locations.domain.port;

import app.meethalfway.shared.domain.Coordinate;
import java.util.List;

/**
 * Port for listing establishments near a point.
 *
 * <p><strong>Post-MVP.</strong> Places are out of scope for the MVP
 * (Requirement 12.3); this interface is defined so the future "list establishments
 * in the zone" feature only requires writing a concrete adapter, not restructuring
 * the core (see design "Ports to the Outside World"). The MVP ships only a no-op
 * stub implementation in the adapters layer.
 */
public interface PlacesProvider {

    /**
     * Lists establishments near {@code center} subject to {@code query}.
     *
     * @param center the point to search around
     * @param query  radius and result-count constraints
     * @return the matching establishments; never {@code null}, possibly empty
     */
    List<Place> nearby(Coordinate center, PlaceQuery query);
}
