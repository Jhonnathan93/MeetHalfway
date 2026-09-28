package app.meethalfway.routing.domain.port;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.shared.domain.TransportMode;

/**
 * Outbound port for computing real travel time between two points for a shared
 * transport mode (Requirements 2.1, 5.2). The engine depends only on this
 * interface; concrete routing services live in the {@code adapters} layer and
 * are injected explicitly, so the routing provider can be swapped without
 * touching the core.
 *
 * <p>Expected failures (timeout, rate limit, unreachable destination, out of
 * coverage) are returned as a {@link RouteResult.Failure} rather than thrown, so
 * the engine can report the specific reason and require the Creator to correct
 * or remove the affected location instead of silently dropping it (Requirement
 * 6.1, 6.3, 6.4).
 */
public interface RoutingProvider {

    /**
     * Computes the travel time from {@code origin} to {@code destination} for the
     * given transport mode.
     *
     * @param origin      the starting point (a participant location)
     * @param destination the candidate meeting point
     * @param mode        the shared transport mode for the meeting
     * @return a {@link RouteResult.Success} with a whole-minute travel time, or a
     *         {@link RouteResult.Failure} carrying the specific reason the route
     *         could not be computed
     */
    RouteResult travelTime(Coordinate origin, Coordinate destination, TransportMode mode);
}
