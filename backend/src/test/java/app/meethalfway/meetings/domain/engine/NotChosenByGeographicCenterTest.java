package app.meethalfway.meetings.domain.engine;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EvaluatedCandidate;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.shared.domain.ParticipantId;
import app.meethalfway.shared.domain.TransportMode;
import app.meethalfway.routing.domain.port.RouteResult;
import app.meethalfway.shared.testing.FakeRoutingProvider;

/**
 * Example-based unit test demonstrating that the meeting point is chosen by
 * real travel time, never by the geographic center of the participants
 * (Requirement 5.3).
 *
 * <p>The scenario is engineered so the arithmetic mean of the participant
 * locations (the {@code Geographic_Centroid}) is a genuine candidate point, yet
 * a different candidate&mdash;further from that centroid&mdash;has a strictly
 * lower {@code Sum_Time}. Because the engine minimizes over measured
 * {@code Travel_Time}s (via {@link MetricCalculator}) rather than geographic
 * distance, the metric-optimal candidate is the far one, proving that a naive
 * midpoint selection would have picked the wrong point.
 *
 * <p>Travel times are supplied by a deterministic {@link FakeRoutingProvider}
 * with pinned per-(origin, candidate) minutes, so the demonstration is
 * self-contained and reproducible. The engine's {@code RecommendationEngine}
 * orchestration (task 7.1) is not yet implemented, so this test drives the
 * comparison directly through {@link MetricCalculator} over the candidate set.
 */
class NotChosenByGeographicCenterTest {

    private static final TransportMode MODE = TransportMode.DRIVING;

    private final MetricCalculator metrics = new MetricCalculator();

    // Two participants placed symmetrically. Their arithmetic-mean centroid is
    // exactly (6.20, -75.60), which we also register as a candidate point.
    private final ParticipantId alice = new ParticipantId("alice");
    private final ParticipantId bob = new ParticipantId("bob");
    private final Coordinate aliceHome = new Coordinate(6.10, -75.60);
    private final Coordinate bobHome = new Coordinate(6.30, -75.60);

    private final Coordinate centroid = new Coordinate(6.20, -75.60);
    // A candidate noticeably further from the centroid, biased toward Alice.
    private final Coordinate offCentre = new Coordinate(6.12, -75.55);

    @Test
    void selectedPointDiffersFromGeographicCentroidBecauseTravelTimeDominates() {
        // Engineer travel times so the off-centre candidate has a strictly lower
        // Sum_Time than the geographic centroid. A distance-based midpoint would
        // pick the centroid; a travel-time optimizer must pick offCentre.
        FakeRoutingProvider routing = FakeRoutingProvider.builder()
                // Centroid: balanced but slow for both -> Sum_Time = 30 + 30 = 60.
                .withFixedTime(aliceHome, centroid, 30)
                .withFixedTime(bobHome, centroid, 30)
                // Off-centre: much faster overall -> Sum_Time = 10 + 25 = 35.
                .withFixedTime(aliceHome, offCentre, 10)
                .withFixedTime(bobHome, offCentre, 25)
                .build();

        List<Coordinate> candidates = List.of(centroid, offCentre);

        EvaluatedCandidate best = candidates.stream()
                .map(point -> metrics.evaluate(point, travelTimesTo(routing, point)))
                .min(Comparator.comparingDouble(EvaluatedCandidate::sumTime))
                .orElseThrow();

        Coordinate geographicCentroid = geographicCentroid(List.of(aliceHome, bobHome));

        // Sanity: the centroid candidate really is the arithmetic mean of the
        // participant locations (allowing for binary floating-point rounding).
        assertThat(geographicCentroid.lat()).isEqualTo(centroid.lat(), org.assertj.core.api.Assertions.within(1e-9));
        assertThat(geographicCentroid.lng()).isEqualTo(centroid.lng(), org.assertj.core.api.Assertions.within(1e-9));

        // The Sum_Time-optimal point is the off-centre candidate, not the centroid.
        assertThat(best.point()).isEqualTo(offCentre);
        assertThat(best.point()).isNotEqualTo(centroid);
        assertThat(best.sumTime()).isEqualTo(35.0);

        // And the centroid, though geographically central, is strictly worse on
        // real travel time, which is exactly why it is not selected.
        EvaluatedCandidate atCentroid =
                metrics.evaluate(centroid, travelTimesTo(routing, centroid));
        assertThat(atCentroid.sumTime()).isEqualTo(60.0);
        assertThat(best.sumTime()).isLessThan(atCentroid.sumTime());
    }

    /** Asks the routing provider for each participant's whole-minute time to {@code point}. */
    private Map<ParticipantId, Minutes> travelTimesTo(FakeRoutingProvider routing, Coordinate point) {
        Map<ParticipantId, Minutes> times = new LinkedHashMap<>();
        times.put(alice, minutes(routing.travelTime(aliceHome, point, MODE)));
        times.put(bob, minutes(routing.travelTime(bobHome, point, MODE)));
        return times;
    }

    private static Minutes minutes(RouteResult result) {
        return ((RouteResult.Success) result).travelTime();
    }

    /** Arithmetic mean of the participant locations = the Geographic_Centroid. */
    private static Coordinate geographicCentroid(List<Coordinate> locations) {
        double lat = locations.stream().mapToDouble(Coordinate::lat).average().orElseThrow();
        double lng = locations.stream().mapToDouble(Coordinate::lng).average().orElseThrow();
        return new Coordinate(lat, lng);
    }
}
