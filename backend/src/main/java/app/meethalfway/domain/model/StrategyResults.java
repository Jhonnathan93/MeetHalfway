package app.meethalfway.domain.model;

/**
 * The complete set of strategy results for a single computation.
 *
 * <p>Requirement 8.1 requires exactly one result per strategy, so this record
 * bundles the {@link StrategyResult} for each of the three strategies. All three
 * are mandatory; a computation that produced only some of them would be
 * incomplete.
 *
 * @param fastest the Fastest_Strategy result (minimizes {@code Σ tᵢ})
 * @param minimax the Minimax_Strategy result (minimizes {@code max tᵢ})
 * @param fairest the Fairest_Strategy result (minimizes {@code σ(t)} within the
 *                efficiency-tolerant feasible set)
 */
public record StrategyResults(
        StrategyResult fastest,
        StrategyResult minimax,
        StrategyResult fairest) {

    public StrategyResults {
        if (fastest == null) {
            throw new IllegalArgumentException("fastest result must not be null");
        }
        if (minimax == null) {
            throw new IllegalArgumentException("minimax result must not be null");
        }
        if (fairest == null) {
            throw new IllegalArgumentException("fairest result must not be null");
        }
    }
}
