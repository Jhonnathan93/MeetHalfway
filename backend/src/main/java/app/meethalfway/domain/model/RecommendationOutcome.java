package app.meethalfway.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * The result of a recommendation computation, modelled as a sealed algebraic
 * type so callers must handle both the success and the routing-failure case
 * explicitly (Requirement 6). The engine returns this instead of throwing for
 * expected routing failures.
 *
 * <p>There are exactly two variants:
 * <ul>
 *   <li>{@link Success} — the three strategy results, plus an optional
 *       {@link OutlierTradeoff} present only when at least one outlier was
 *       detected (Requirement 7, 8).</li>
 *   <li>{@link RoutingFailure} — one or more {@link RoutingError}s. No
 *       recommendation is produced, so a participant is never silently omitted
 *       (Requirement 6.1, 6.3, 6.4).</li>
 * </ul>
 */
public sealed interface RecommendationOutcome
        permits RecommendationOutcome.Success, RecommendationOutcome.RoutingFailure {

    /**
     * A successful computation carrying one result per strategy and, when
     * applicable, the outlier include-versus-exclude trade-off.
     *
     * @param results  the three strategy results (Requirement 8.1)
     * @param tradeoff the outlier trade-off, present only when at least one
     *                 outlier exists (Requirement 7.2, 7.3); otherwise empty
     */
    record Success(StrategyResults results, Optional<OutlierTradeoff> tradeoff)
            implements RecommendationOutcome {

        public Success {
            if (results == null) {
                throw new IllegalArgumentException("results must not be null");
            }
            if (tradeoff == null) {
                throw new IllegalArgumentException(
                        "tradeoff must not be null; use Optional.empty() when there is no outlier");
            }
        }

        /**
         * Convenience factory for a success with no outlier trade-off.
         *
         * @param results the three strategy results
         * @return a {@code Success} whose {@code tradeoff} is empty
         */
        public static Success of(StrategyResults results) {
            return new Success(results, Optional.empty());
        }

        /**
         * Convenience factory for a success that includes an outlier trade-off.
         *
         * @param results  the three strategy results
         * @param tradeoff the outlier trade-off (must not be null)
         * @return a {@code Success} carrying the given trade-off
         */
        public static Success of(StrategyResults results, OutlierTradeoff tradeoff) {
            if (tradeoff == null) {
                throw new IllegalArgumentException("tradeoff must not be null");
            }
            return new Success(results, Optional.of(tradeoff));
        }
    }

    /**
     * A failed computation: one or more participant locations could not be
     * routed (Requirement 6). No recommendations are produced.
     *
     * @param errors the routing errors, one per affected participant location;
     *               must be non-null and non-empty. The list is defensively
     *               copied into an unmodifiable list.
     */
    record RoutingFailure(List<RoutingError> errors) implements RecommendationOutcome {

        public RoutingFailure {
            if (errors == null) {
                throw new IllegalArgumentException("errors must not be null");
            }
            if (errors.isEmpty()) {
                throw new IllegalArgumentException("a routing failure must carry at least one error");
            }
            if (errors.contains(null)) {
                throw new IllegalArgumentException("errors must not contain a null entry");
            }
            errors = List.copyOf(errors);
        }
    }
}
