package app.meethalfway.adapters.web.support;

import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.TransportMode;
import app.meethalfway.domain.port.RouteResult;
import app.meethalfway.domain.port.RoutingProvider;

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
