package app.meethalfway.meetings.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.meethalfway.meetings.domain.model.StrategyResult;
import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.shared.domain.ParticipantId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TieBreakerTest {

    private final TieBreaker tieBreaker = new TieBreaker(0.5);

    private static StrategyResult candidate(Coordinate point, double sumTime, int maxTime, double stdDev) {
        Map<ParticipantId, Minutes> perParticipant = new LinkedHashMap<>();
        perParticipant.put(new ParticipantId("p1"), new Minutes(maxTime));
        return new StrategyResult(point, perParticipant, sumTime, maxTime, stdDev);
    }

    @Test
    void epsilonBoundaryIsStrict() {
        assertThat(tieBreaker.areEqual(10.0, 10.4)).isTrue();
        assertThat(tieBreaker.areEqual(10.0, 10.5)).isFalse();
        assertThat(tieBreaker.areEqual(10.0, 9.7)).isTrue();
    }

    @Test
    void epsilonTieFallsThroughToTheNextMetric() {
        Coordinate centroid = new Coordinate(6.25, -75.55);
        StrategyResult lowerMax = candidate(new Coordinate(6.2, -75.6), 10.0, 4, 2.0);
        StrategyResult higherMax = candidate(new Coordinate(6.3, -75.5), 10.3, 9, 2.0);

        StrategyResult winner = tieBreaker.selectWinner(
                List.of(higherMax, lowerMax), centroid,
                List.of(StrategyResult::sumTime, StrategyResult::maxTime, StrategyResult::stdDev));

        assertThat(winner).isEqualTo(lowerMax);
    }

    @Test
    void valueOutsideEpsilonWinsBeforeSecondaryMetrics() {
        Coordinate centroid = new Coordinate(6.25, -75.55);
        StrategyResult lowerSum = candidate(new Coordinate(6.2, -75.6), 10.0, 20, 10.0);
        StrategyResult betterSecondary = candidate(new Coordinate(6.3, -75.5), 10.6, 1, 0.0);

        StrategyResult winner = tieBreaker.selectWinner(
                List.of(betterSecondary, lowerSum), centroid,
                List.of(StrategyResult::sumTime, StrategyResult::maxTime, StrategyResult::stdDev));

        assertThat(winner).isEqualTo(lowerSum);
    }

    @Test
    void epsilonSelectionIsIndependentOfCandidateOrder() {
        StrategyResult boundary = candidate(new Coordinate(0.0, 0.0), 0.0, 0, 0.5);
        StrategyResult farther = candidate(new Coordinate(0.0, 1.0), 0.0, 1, 0.0);
        StrategyResult nearer = candidate(new Coordinate(0.0, 0.0), 0.0, 1, 0.01);
        Coordinate centroid = GeographicCentroid.of(List.of(boundary.point(), farther.point(), nearer.point()));
        List<StrategyResult> priorities = List.of(boundary, farther, nearer);
        List<java.util.function.ToDoubleFunction<StrategyResult>> metrics =
                List.of(StrategyResult::stdDev, StrategyResult::sumTime, StrategyResult::maxTime);

        assertThat(tieBreaker.selectWinner(priorities, centroid, metrics)).isEqualTo(nearer);
        assertThat(tieBreaker.selectWinner(List.of(nearer, farther, boundary), centroid, metrics))
                .isEqualTo(nearer);
    }

    @Test
    void centroidDistanceAndCoordinatesResolveRemainingTies() {
        Coordinate centroid = new Coordinate(6.25, -75.55);
        StrategyResult near = candidate(new Coordinate(6.25, -75.551), 10.0, 5, 1.0);
        StrategyResult far = candidate(new Coordinate(6.40, -75.40), 10.0, 5, 1.0);

        StrategyResult winner = tieBreaker.selectWinner(
                List.of(far, near), centroid,
                List.of(StrategyResult::sumTime, StrategyResult::maxTime, StrategyResult::stdDev));

        assertThat(winner).isEqualTo(near);
    }

    @Test
    void constructorRejectsInvalidEpsilon() {
        assertThatThrownBy(() -> new TieBreaker(0.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TieBreaker(-1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TieBreaker(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void geographicCentroidIsArithmeticMeanAndRejectsEmptyInput() {
        Coordinate centroid = GeographicCentroid.of(
                List.of(new Coordinate(0.0, 0.0), new Coordinate(2.0, 4.0), new Coordinate(4.0, 8.0)));
        assertThat(centroid.lat()).isEqualTo(2.0);
        assertThat(centroid.lng()).isEqualTo(4.0);
        assertThatThrownBy(() -> GeographicCentroid.of(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeographicCentroid.of(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
