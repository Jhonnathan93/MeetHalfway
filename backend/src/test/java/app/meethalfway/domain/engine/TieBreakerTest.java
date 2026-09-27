package app.meethalfway.domain.engine;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.EvaluatedCandidate;
import app.meethalfway.domain.model.Minutes;
import app.meethalfway.domain.model.ParticipantId;

/**
 * Example-based unit tests for {@link TieBreaker} and {@link GeographicCentroid}.
 * Property 9 (tie-break totality across randomized candidates) is covered by a
 * separate property test (task 5.9); these tests pin down the ε-tolerant
 * comparison, comparator fall-through, and the non-ε centroid total order.
 */
class TieBreakerTest {

    private final TieBreaker tieBreaker = new TieBreaker(0.5);

    private static EvaluatedCandidate candidate(
            Coordinate point, double sumTime, int maxTime, double stdDev) {
        // perParticipant is not exercised by the comparators; a single valid entry
        // satisfies EvaluatedCandidate's invariant. A mutable map is used because
        // EvaluatedCandidate's constructor calls containsKey(null), which throws on
        // the immutable maps returned by Map.of.
        Map<ParticipantId, Minutes> perParticipant = new java.util.LinkedHashMap<>();
        perParticipant.put(new ParticipantId("p1"), new Minutes(maxTime));
        return new EvaluatedCandidate(point, perParticipant, sumTime, maxTime, stdDev);
    }

    @Test
    void areEqualUsesStrictlyLessThanEpsilon() {
        assertThat(tieBreaker.areEqual(10.0, 10.4)).isTrue();
        assertThat(tieBreaker.areEqual(10.0, 10.5)).isFalse(); // exactly ε is not "equal"
        assertThat(tieBreaker.areEqual(10.0, 10.6)).isFalse();
        assertThat(tieBreaker.areEqual(10.0, 9.7)).isTrue();
    }

    @Test
    void metricComparatorTreatsWithinEpsilonAsTied() {
        Coordinate p = new Coordinate(6.2, -75.6);
        EvaluatedCandidate a = candidate(p, 10.0, 5, 1.0);
        EvaluatedCandidate b = candidate(p, 10.4, 5, 1.0); // within ε on Σ
        assertThat(tieBreaker.bySumTime().compare(a, b)).isZero();
    }

    @Test
    void metricComparatorOrdersByLowerValueBeyondEpsilon() {
        Coordinate p = new Coordinate(6.2, -75.6);
        EvaluatedCandidate low = candidate(p, 10.0, 5, 1.0);
        EvaluatedCandidate high = candidate(p, 20.0, 5, 1.0);
        assertThat(tieBreaker.bySumTime().compare(low, high)).isNegative();
        assertThat(tieBreaker.bySumTime().compare(high, low)).isPositive();
    }

    @Test
    void chainFallsThroughToNextComparatorWhenPrimaryIsTiedWithinEpsilon() {
        Coordinate centroid = new Coordinate(6.25, -75.55);
        // Σ within ε (tied), so max_time decides: 'lowerMax' wins.
        EvaluatedCandidate lowerMax = candidate(new Coordinate(6.2, -75.6), 10.0, 4, 2.0);
        EvaluatedCandidate higherMax = candidate(new Coordinate(6.3, -75.5), 10.3, 9, 2.0);

        var order = tieBreaker.totalOrder(
                centroid, List.of(tieBreaker.bySumTime(), tieBreaker.byMaxTime(), tieBreaker.byStdDev()));

        assertThat(tieBreaker.selectWinner(List.of(higherMax, lowerMax), order)).isEqualTo(lowerMax);
    }

    @Test
    void centroidDistanceBreaksResidualTieWithoutEpsilon() {
        Coordinate centroid = new Coordinate(6.25, -75.55);
        // Every metric identical; only distance-to-centroid differs. The distances
        // differ by far less than ε minutes would allow, but the centroid comparator
        // ignores ε, so the nearer candidate strictly wins.
        EvaluatedCandidate near = candidate(new Coordinate(6.25, -75.551), 10.0, 5, 1.0);
        EvaluatedCandidate far = candidate(new Coordinate(6.40, -75.40), 10.0, 5, 1.0);

        var order = tieBreaker.totalOrder(
                centroid, List.of(tieBreaker.bySumTime(), tieBreaker.byMaxTime(), tieBreaker.byStdDev()));

        assertThat(tieBreaker.selectWinner(List.of(far, near), order)).isEqualTo(near);
    }

    @Test
    void selectWinnerYieldsSingleDeterministicWinner() {
        Coordinate centroid = new Coordinate(6.25, -75.55);
        EvaluatedCandidate a = candidate(new Coordinate(6.20, -75.60), 12.0, 6, 2.0);
        EvaluatedCandidate b = candidate(new Coordinate(6.25, -75.55), 10.0, 5, 1.0);
        EvaluatedCandidate c = candidate(new Coordinate(6.30, -75.50), 15.0, 7, 3.0);

        var order = tieBreaker.totalOrder(
                centroid, List.of(tieBreaker.bySumTime(), tieBreaker.byMaxTime(), tieBreaker.byStdDev()));

        // b has the lowest Σ, so it wins Fastest-style ordering, and the result is
        // stable regardless of input order.
        assertThat(tieBreaker.selectWinner(List.of(a, b, c), order)).isEqualTo(b);
        assertThat(tieBreaker.selectWinner(List.of(c, b, a), order)).isEqualTo(b);
    }

    @Test
    void constructorRejectsNonPositiveEpsilon() {
        assertThatThrownBy(() -> new TieBreaker(0.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TieBreaker(-1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TieBreaker(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void geographicCentroidIsArithmeticMean() {
        Coordinate centroid = GeographicCentroid.of(
                List.of(new Coordinate(0.0, 0.0), new Coordinate(2.0, 4.0), new Coordinate(4.0, 8.0)));
        assertThat(centroid.lat()).isEqualTo(2.0);
        assertThat(centroid.lng()).isEqualTo(4.0);
    }

    @Test
    void geographicCentroidRejectsEmptyOrNull() {
        assertThatThrownBy(() -> GeographicCentroid.of(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeographicCentroid.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
