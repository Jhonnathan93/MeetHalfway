package app.meethalfway.meetings.web.support;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.shared.domain.TransportMode;
import app.meethalfway.routing.domain.port.RouteResult;
import app.meethalfway.routing.domain.port.RoutingProvider;

/**
 * A {@link RoutingProvider} that is never actually consulted in web-adapter
 * tests (the {@link StubRecommendationEngine} short-circuits computation). It
 * exists only to satisfy the non-null dependency of {@code ComputeRecommendations}.
 */
public final class NoOpRoutingProvider implements RoutingProvider {

    @Override
    public RouteResult travelTime(Coordinate origin, Coordinate destination, TransportMode mode) {
        throw new UnsupportedOperationException("routing is not exercised in this test");
    }
}
