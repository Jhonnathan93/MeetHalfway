package app.meethalfway.meetings.domain.engine;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.StrategyResult;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.shared.domain.ParticipantId;

/**
 * Example-based unit tests for {@link FairestSelector}, pinning down the
 * efficiency-feasibility restriction ({@code Σ ≤ 1.15 · T* + ε}) and the
 * strategy's key order {@code σ → Σ → max → distance-to-centroid} with its
 * ε-tolerant fall-through (Requirements 4.1–4.7). The randomized feasibility and
 * optimality properties are covered separately by the property tests.
 */
class FairestSelectorTest {

    private static final Coordinate CENTROID = new Coordinate(6.25, -75.55);

    private final TieBreaker tieBreaker = new TieBreaker(0.5);
    private final FairestSelector selector = new FairestSelector(tieBreaker);

    private static StrategyResult candidate(
            Coordinate point, double sumTime, int maxTime, double stdDev) {
        Map<ParticipantId, Minutes> perParticipant = new java.util.LinkedHashMap<>();
        perParticipant.put(new ParticipantId("p1"), new Minutes(maxTime));
        return new StrategyResult(point, perParticipant, sumTime, maxTime, stdDev);
    }

    @Test
    void selectsFeasibleCandidateWithLowestStdDev() {
        // Req 4.4: within the feasible set, minimize Std_Dev.
        // T* = 10.0, threshold = 1.15 * 10 + 0.5 = 12.0.
        StrategyResult optimum = candidate(new Coordinate(6.20, -75.60), 10.0, 8, 3.0);
        StrategyResult fairFeasible = candidate(new Coordinate(6.30, -75.50), 11.5, 5, 1.0);

        assertThat(selector.select(List.of(optimum, fairFeasible), CENTROID)).isEqualTo(fairFeasible);
        // Deterministic regardless of input order.
        assertThat(selector.select(List.of(fairFeasible, optimum), CENTROID)).isEqualTo(fairFeasible);
    }

    @Test
    void excludesInfeasibleLowStdDevCandidate() {
        // Req 4.2: a low-σ candidate whose Σ exceeds 1.15 · T* + ε is NOT eligible,
        // so the feasible higher-σ candidate wins instead.
        // T* = 10.0, threshold = 1.15 * 10 + 0.5 = 12.0.
        StrategyResult optimum = candidate(new Coordinate(6.20, -75.60), 10.0, 8, 4.0);
        StrategyResult feasible = candidate(new Coordinate(6.30, -75.50), 12.0, 6, 2.0);
        // Infeasible: Σ = 20 > 12.0 despite the lowest Std_Dev of all.
        StrategyResult infeasibleFair = candidate(new Coordinate(6.10, -75.70), 20.0, 5, 0.1);

        assertThat(selector.select(List.of(optimum, feasible, infeasibleFair), CENTROID))
                .isEqualTo(feasible);
    }

    @Test
    void includesOnlyTheOptimumWhenAllOthersAreInfeasible() {
        // Feasible set is never empty: the T*-achieving candidate is always feasible
        // even if it has the highest Std_Dev, when every other candidate is too costly.
        // T* = 10.0, threshold = 12.0; the low-σ candidate at Σ = 30 is excluded.
        StrategyResult optimum = candidate(new Coordinate(6.20, -75.60), 10.0, 8, 5.0);
        StrategyResult infeasibleFair = candidate(new Coordinate(6.30, -75.50), 30.0, 5, 0.2);

        assertThat(selector.select(List.of(infeasibleFair, optimum), CENTROID)).isEqualTo(optimum);
    }

    @Test
    void breaksStdDevTieByLowerSumTimeAmongFeasible() {
        // Req 4.5: σ tied within ε among feasible candidates, so lower Sum_Time wins.
        // T* = 10.0, threshold = 12.0; both feasible.
        StrategyResult lowerSum = candidate(new Coordinate(6.20, -75.60), 10.0, 5, 2.0);
        StrategyResult higherSum = candidate(new Coordinate(6.30, -75.50), 11.5, 4, 2.3);

        assertThat(selector.select(List.of(higherSum, lowerSum), CENTROID)).isEqualTo(lowerSum);
    }

    @Test
    void breaksSumTimeTieByLowerMaxTimeAmongFeasible() {
        // Req 4.6: σ and Σ tied within ε among feasible candidates, so lower Max_Time wins.
        // T* = 10.0, threshold = 12.0; both feasible.
        StrategyResult lowerMax = candidate(new Coordinate(6.20, -75.60), 10.0, 4, 2.0);
        StrategyResult higherMax = candidate(new Coordinate(6.30, -75.50), 10.3, 9, 2.3);

        assertThat(selector.select(List.of(higherMax, lowerMax), CENTROID)).isEqualTo(lowerMax);
    }

    @Test
    void breaksMaxTimeTieByClosestToCentroid() {
        // Req 4.7: all metrics tied within ε among feasible candidates, so nearest
        // to centroid wins (compared without ε).
        StrategyResult near = candidate(new Coordinate(6.25, -75.551), 10.0, 5, 2.0);
        StrategyResult far = candidate(new Coordinate(6.40, -75.40), 10.0, 5, 2.0);

        assertThat(selector.select(List.of(far, near), CENTROID)).isEqualTo(near);
    }

    @Test
    void rejectsNullTieBreaker() {
        assertThatThrownBy(() -> new FairestSelector(null))
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
        StrategyResult any = candidate(new Coordinate(6.20, -75.60), 10.0, 5, 1.0);
        assertThatThrownBy(() -> selector.select(List.of(any), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
