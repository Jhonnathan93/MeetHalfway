package app.meethalfway.routing.domain.port;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * Computes a travel-time matrix with one row per origin and one column per
     * destination. Providers with a native matrix API can override this method
     * to avoid one network request per pair; the default keeps simple providers
     * and test fakes source-compatible.
     */
    default List<List<RouteResult>> travelTimes(
            List<Coordinate> origins, List<Coordinate> destinations, TransportMode mode) {
        if (origins == null || destinations == null || mode == null) {
            throw new IllegalArgumentException("origins, destinations and mode must not be null");
        }

        List<List<RouteResult>> matrix = new ArrayList<>(origins.size());
        for (Coordinate origin : origins) {
            if (origin == null) {
                throw new IllegalArgumentException("origins must not contain null coordinates");
            }
            List<RouteResult> row = new ArrayList<>(destinations.size());
            for (Coordinate destination : destinations) {
                if (destination == null) {
                    throw new IllegalArgumentException("destinations must not contain null coordinates");
                }
                row.add(travelTime(origin, destination, mode));
            }
            matrix.add(List.copyOf(row));
        }
        return List.copyOf(matrix);
    }
}
