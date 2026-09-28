package app.meethalfway.meetings.domain.engine;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EvaluatedCandidate;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.shared.domain.ParticipantId;

/**
 * Example-based unit tests for {@link FastestSelector}, pinning down the
 * strategy's key order {@code Σ → max → σ → distance-to-centroid} and its
 * ε-tolerant fall-through (Requirements 2.2, 2.3, 2.4, 2.5). The randomized
 * total-order property is covered separately by the tie-break property test.
 */
class FastestSelectorTest {

    private static final Coordinate CENTROID = new Coordinate(6.25, -75.55);

    private final TieBreaker tieBreaker = new TieBreaker(0.5);
    private final FastestSelector selector = new FastestSelector(tieBreaker);

    private static EvaluatedCandidate candidate(
            Coordinate point, double sumTime, int maxTime, double stdDev) {
        Map<ParticipantId, Minutes> perParticipant = new java.util.LinkedHashMap<>();
        perParticipant.put(new ParticipantId("p1"), new Minutes(maxTime));
        return new EvaluatedCandidate(point, perParticipant, sumTime, maxTime, stdDev);
    }

    @Test
    void selectsCandidateWithLowestSumTime() {
        // Req 2.2: minimize Sum_Time.
        EvaluatedCandidate low = candidate(new Coordinate(6.20, -75.60), 10.0, 8, 3.0);
        EvaluatedCandidate mid = candidate(new Coordinate(6.30, -75.50), 15.0, 5, 1.0);
        EvaluatedCandidate high = candidate(new Coordinate(6.10, -75.70), 20.0, 4, 0.5);

        assertThat(selector.select(List.of(mid, high, low), CENTROID)).isEqualTo(low);
        // Deterministic regardless of input order.
        assertThat(selector.select(List.of(low, mid, high), CENTROID)).isEqualTo(low);
    }

    @Test
    void breaksSumTimeTieByLowerMaxTime() {
        // Req 2.3: Σ tied within ε, so lower Max_Time wins.
        EvaluatedCandidate lowerMax = candidate(new Coordinate(6.20, -75.60), 10.0, 4, 3.0);
        EvaluatedCandidate higherMax = candidate(new Coordinate(6.30, -75.50), 10.3, 9, 1.0);

        assertThat(selector.select(List.of(higherMax, lowerMax), CENTROID)).isEqualTo(lowerMax);
    }

    @Test
    void breaksMaxTimeTieByLowerStdDev() {
        // Req 2.4: Σ and max tied within ε, so lower Std_Dev wins.
        EvaluatedCandidate lowerStdDev = candidate(new Coordinate(6.20, -75.60), 10.0, 5, 1.0);
        EvaluatedCandidate higherStdDev = candidate(new Coordinate(6.30, -75.50), 10.3, 5, 4.0);

        assertThat(selector.select(List.of(higherStdDev, lowerStdDev), CENTROID)).isEqualTo(lowerStdDev);
    }

    @Test
    void breaksStdDevTieByClosestToCentroid() {
        // Req 2.5: all metrics tied within ε, so nearest to centroid wins (non-ε).
        EvaluatedCandidate near = candidate(new Coordinate(6.25, -75.551), 10.0, 5, 1.0);
        EvaluatedCandidate far = candidate(new Coordinate(6.40, -75.40), 10.0, 5, 1.0);

        assertThat(selector.select(List.of(far, near), CENTROID)).isEqualTo(near);
    }

    @Test
    void rejectsNullTieBreaker() {
        assertThatThrownBy(() -> new FastestSelector(null))
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
