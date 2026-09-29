package app.meethalfway.meetings.web.support;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.shared.domain.TransportMode;
import app.meethalfway.routing.domain.port.RouteResult;
import app.meethalfway.routing.domain.port.RoutingProvider;

/**
 * A {@link RoutingProvider} that fails if accidentally used by route-metadata
 * tests, which construct the real engine but never execute a recommendation.
 */
public final class NoOpRoutingProvider implements RoutingProvider {

    @Override
    public RouteResult travelTime(Coordinate origin, Coordinate destination, TransportMode mode) {
        throw new UnsupportedOperationException("routing is not exercised in this test");
    }
}
