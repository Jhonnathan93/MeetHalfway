package app.meethalfway.domain.engine;

import java.util.List;

import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.EvaluatedCandidate;

/**
 * Selects the {@code Fastest_Strategy} winner: the candidate that minimizes the
 * group's total travel time {@code Sum_Time (Σ tᵢ)} (Requirement 2.2).
 *
 * <p>Ties are resolved through the deterministic, ε-tolerant key order mandated
 * by Requirement 2:
 * <ol>
 *   <li>{@code Σ} — lower {@code Sum_Time} wins (Req 2.2).</li>
 *   <li>{@code max} — on a {@code Σ} tie within ε, lower {@code Max_Time} wins
 *       (Req 2.3).</li>
 *   <li>{@code σ} — on a further {@code max} tie within ε, lower {@code Std_Dev}
 *       wins (Req 2.4).</li>
 *   <li>{@code distance-to-centroid} — on a further {@code σ} tie within ε, the
 *       candidate closest to the {@code Geographic_Centroid} wins, compared
 *       without ε so the result is always a single, deterministic winner
 *       (Req 2.5).</li>
 * </ol>
 *
 * <p>The ε tolerance and the total-order composition are delegated to the
 * injected {@link TieBreaker}; this selector only fixes the strategy-specific
 * priority order of the metric comparators. The {@code Geographic_Centroid} is
 * supplied by the caller via {@link #select(List, Coordinate)}.
 *
 * <p>This is framework-free domain logic with no dependency on HTTP, persistence,
 * or any provider.
 */
public final class FastestSelector implements StrategySelector {

    private final TieBreaker tieBreaker;

    /**
     * Creates a Fastest selector backed by the given tie-breaker. The ε tolerance
     * is carried by the {@link TieBreaker} itself (from {@code EngineConfig}).
     *
     * @param tieBreaker the shared tie-breaker used to build the ε-tolerant
     *                  comparator chain; must not be null
     * @throws IllegalArgumentException if {@code tieBreaker} is null
     */
    public FastestSelector(TieBreaker tieBreaker) {
        if (tieBreaker == null) {
            throw new IllegalArgumentException("tieBreaker must not be null");
        }
        this.tieBreaker = tieBreaker;
    }

    /**
     * Selects the Fastest winner using the key order
     * {@code Σ → max → σ → distance-to-centroid}.
     *
     * @param candidates the evaluated candidates; must be non-null and non-empty
     * @param centroid   the {@code Geographic_Centroid} for the final tiebreak;
     *                  must not be null
     * @return the single Fastest-strategy winner
     * @throws IllegalArgumentException if {@code candidates} is null/empty or
     *                                  {@code centroid} is null
     */
    @Override
    public EvaluatedCandidate select(List<EvaluatedCandidate> candidates, Coordinate centroid) {
        return tieBreaker.selectWinner(
                candidates,
                tieBreaker.totalOrder(
                        centroid,
                        List.of(
                                tieBreaker.bySumTime(),
                                tieBreaker.byMaxTime(),
                                tieBreaker.byStdDev())));
    }
}
