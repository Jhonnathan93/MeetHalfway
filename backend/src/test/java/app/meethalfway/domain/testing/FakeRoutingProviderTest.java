package app.meethalfway.domain.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.Minutes;
import app.meethalfway.domain.model.TransportMode;
import app.meethalfway.domain.port.RouteResult;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FakeRoutingProvider}, verifying it is a deterministic,
 * offline routing double that supports the engineered scenarios the engine
 * property tests rely on: reproducible matrices, injected outliers, injected
 * failures, and precisely pinned times for ε-ties.
 */
class FakeRoutingProviderTest {

    private static final Coordinate ORIGIN_A = new Coordinate(6.20, -75.57);
    private static final Coordinate ORIGIN_B = new Coordinate(6.25, -75.56);
    private static final Coordinate CANDIDATE = new Coordinate(6.24, -75.58);

    @Test
    void returnsReproducibleWholeMinuteTimesForTheSameQuery() {
        FakeRoutingProvider routing = FakeRoutingProvider.withDefaults();

        RouteResult first = routing.travelTime(ORIGIN_A, CANDIDATE, TransportMode.DRIVING);
        RouteResult second = routing.travelTime(ORIGIN_A, CANDIDATE, TransportMode.DRIVING);

        assertThat(first).isInstanceOf(RouteResult.Success.class);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void walkingIsSlowerThanDrivingForTheSamePair() {
        FakeRoutingProvider routing = FakeRoutingProvider.withDefaults();

        int driving = minutes(routing.travelTime(ORIGIN_A, CANDIDATE, TransportMode.DRIVING));
        int walking = minutes(routing.travelTime(ORIGIN_A, CANDIDATE, TransportMode.WALKING));

        assertThat(walking).isGreaterThan(driving);
    }

    @Test
    void pinnedTimesOverrideTheDistanceModelForEngineeredTies() {
        FakeRoutingProvider routing = FakeRoutingProvider.builder()
                .withFixedTime(ORIGIN_A, CANDIDATE, 10)
                .withFixedTime(ORIGIN_B, CANDIDATE, 10)
                .build();

        assertThat(minutes(routing.travelTime(ORIGIN_A, CANDIDATE, TransportMode.DRIVING)))
                .isEqualTo(10);
        assertThat(minutes(routing.travelTime(ORIGIN_B, CANDIDATE, TransportMode.DRIVING)))
                .isEqualTo(10);
    }

    @Test
    void injectedOutlierOriginProducesExaggeratedTimes() {
        FakeRoutingProvider baseline = FakeRoutingProvider.withDefaults();
        int normal = minutes(baseline.travelTime(ORIGIN_A, CANDIDATE, TransportMode.DRIVING));

        FakeRoutingProvider withOutlier = FakeRoutingProvider.builder()
                .withOutlierOrigin(ORIGIN_A, 5.0)
                .build();
        int exaggerated = minutes(withOutlier.travelTime(ORIGIN_A, CANDIDATE, TransportMode.DRIVING));

        assertThat(exaggerated).isGreaterThan(normal);
    }

    @Test
    void injectedFailingOriginReturnsFailureWithReason() {
        FakeRoutingProvider routing = FakeRoutingProvider.builder()
                .withFailingOrigin(ORIGIN_A, "location outside routing coverage")
                .build();

        RouteResult result = routing.travelTime(ORIGIN_A, CANDIDATE, TransportMode.DRIVING);

        assertThat(result).isInstanceOf(RouteResult.Failure.class);
        assertThat(((RouteResult.Failure) result).reason())
                .isEqualTo("location outside routing coverage");
    }

    @Test
    void failingOriginTakesPrecedenceOverPinnedTime() {
        FakeRoutingProvider routing = FakeRoutingProvider.builder()
                .withFixedTime(ORIGIN_A, CANDIDATE, 10)
                .withFailingOrigin(ORIGIN_A, "timeout")
                .build();

        assertThat(routing.travelTime(ORIGIN_A, CANDIDATE, TransportMode.DRIVING))
                .isInstanceOf(RouteResult.Failure.class);
    }

    @Test
    void rejectsNullArguments() {
        FakeRoutingProvider routing = FakeRoutingProvider.withDefaults();

        assertThatThrownBy(() -> routing.travelTime(null, CANDIDATE, TransportMode.DRIVING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static int minutes(RouteResult result) {
        assertThat(result).isInstanceOf(RouteResult.Success.class);
        Minutes travelTime = ((RouteResult.Success) result).travelTime();
        return travelTime.value();
    }
}
