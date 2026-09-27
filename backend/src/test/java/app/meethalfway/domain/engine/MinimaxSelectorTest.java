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
 * Example-based unit tests for {@link MinimaxSelector}, pinning down the
 * strategy's key order {@code max → Σ → σ → distance-to-centroid} and its
 * ε-tolerant fall-through (Requirements 3.1, 3.2, 3.3, 3.4). The randomized
 * total-order property is covered separately by the tie-break property test.
 */
class MinimaxSelectorTest {

    private static final Coordinate CENTROID = new Coordinate(6.25, -75.55);

    private final TieBreaker tieBreaker = new TieBreaker(0.5);
    private final MinimaxSelector selector = new MinimaxSelector(tieBreaker);

    private static EvaluatedCandidate candidate(
            Coordinate point, double sumTime, int maxTime, double stdDev) {
        Map<ParticipantId, Minutes> perParticipant = new java.util.LinkedHashMap<>();
        perParticipant.put(new ParticipantId("p1"), new Minutes(maxTime));
        return new EvaluatedCandidate(point, perParticipant, sumTime, maxTime, stdDev);
    }

    @Test
    void selectsCandidateWithLowestMaxTime() {
        // Req 3.1: minimize Max_Time, independent of Sum_Time.
        EvaluatedCandidate lowMax = candidate(new Coordinate(6.20, -75.60), 20.0, 4, 3.0);
        EvaluatedCandidate midMax = candidate(new Coordinate(6.30, -75.50), 15.0, 6, 1.0);
        EvaluatedCandidate highMax = candidate(new Coordinate(6.10, -75.70), 10.0, 9, 0.5);

        assertThat(selector.select(List.of(midMax, highMax, lowMax), CENTROID)).isEqualTo(lowMax);
        // Deterministic regardless of input order.
        assertThat(selector.select(List.of(lowMax, midMax, highMax), CENTROID)).isEqualTo(lowMax);
    }

    @Test
    void breaksMaxTimeTieByLowerSumTime() {
        // Req 3.2: Max_Time tied within ε, so lower Sum_Time wins.
        EvaluatedCandidate lowerSum = candidate(new Coordinate(6.20, -75.60), 10.0, 5, 3.0);
        EvaluatedCandidate higherSum = candidate(new Coordinate(6.30, -75.50), 18.0, 5, 1.0);

        assertThat(selector.select(List.of(higherSum, lowerSum), CENTROID)).isEqualTo(lowerSum);
    }

    @Test
    void breaksSumTimeTieByLowerStdDev() {
        // Req 3.3: Max_Time and Sum_Time tied within ε, so lower Std_Dev wins.
        EvaluatedCandidate lowerStdDev = candidate(new Coordinate(6.20, -75.60), 10.0, 5, 1.0);
        EvaluatedCandidate higherStdDev = candidate(new Coordinate(6.30, -75.50), 10.3, 5, 4.0);

        assertThat(selector.select(List.of(higherStdDev, lowerStdDev), CENTROID)).isEqualTo(lowerStdDev);
    }

    @Test
    void breaksStdDevTieByClosestToCentroid() {
        // Req 3.4: all metrics tied within ε, so nearest to centroid wins (non-ε).
        EvaluatedCandidate near = candidate(new Coordinate(6.25, -75.551), 10.0, 5, 1.0);
        EvaluatedCandidate far = candidate(new Coordinate(6.40, -75.40), 10.0, 5, 1.0);

        assertThat(selector.select(List.of(far, near), CENTROID)).isEqualTo(near);
    }

    @Test
    void rejectsNullTieBreaker() {
        assertThatThrownBy(() -> new MinimaxSelector(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullOrEmptyCandidates() {
        assertThatThrownBy(() -> selector.select(List.of(), CENTROID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> selector.select(null, CENTROID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullCentroid() {
        EvaluatedCandidate any = candidate(new Coordinate(6.20, -75.60), 10.0, 5, 1.0);
        assertThatThrownBy(() -> selector.select(List.of(any), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
