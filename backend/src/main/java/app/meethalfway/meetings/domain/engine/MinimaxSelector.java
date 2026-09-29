package app.meethalfway.meetings.domain.engine;

import java.util.List;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.StrategyResult;

/**
 * Selects the {@code Minimax_Strategy} winner: the candidate that minimizes the
 * worst individual trip {@code Max_Time (max tᵢ)} (Requirement 3.1).
 *
 * <p>Ties are resolved through the deterministic, ε-tolerant key order mandated
 * by Requirement 3:
 * <ol>
 *   <li>{@code max} — lower {@code Max_Time} wins (Req 3.1).</li>
 *   <li>{@code Σ} — on a {@code max} tie within ε, lower {@code Sum_Time} wins
 *       (Req 3.2).</li>
 *   <li>{@code σ} — on a further {@code Σ} tie within ε, lower {@code Std_Dev}
 *       wins (Req 3.3).</li>
 *   <li>{@code distance-to-centroid} — on a further {@code σ} tie within ε, the
 *       candidate closest to the {@code Geographic_Centroid} wins, compared
 *       without ε so the result is always a single, deterministic winner
 *       (Req 3.4).</li>
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
public final class MinimaxSelector {

    private final TieBreaker tieBreaker;

    /**
     * Creates a Minimax selector backed by the given tie-breaker. The ε tolerance
     * is carried by the {@link TieBreaker} itself (from {@code EngineConfig}).
     *
     * @param tieBreaker the shared tie-breaker used to build the ε-tolerant
     *                  comparator chain; must not be null
     * @throws IllegalArgumentException if {@code tieBreaker} is null
     */
    public MinimaxSelector(TieBreaker tieBreaker) {
        if (tieBreaker == null) {
            throw new IllegalArgumentException("tieBreaker must not be null");
        }
        this.tieBreaker = tieBreaker;
    }

    /**
     * Selects the Minimax winner using the key order
     * {@code max → Σ → σ → distance-to-centroid}.
     *
     * @param candidates the evaluated candidates; must be non-null and non-empty
     * @param centroid   the {@code Geographic_Centroid} for the final tiebreak;
     *                  must not be null
     * @return the single Minimax-strategy winner
     * @throws IllegalArgumentException if {@code candidates} is null/empty or
     *                                  {@code centroid} is null
     */
    public StrategyResult select(List<StrategyResult> candidates, Coordinate centroid) {
        return tieBreaker.selectWinner(
                candidates,
                centroid,
                List.of(StrategyResult::maxTime, StrategyResult::sumTime, StrategyResult::stdDev));
    }
}
