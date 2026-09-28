package app.meethalfway.meetings.domain.engine;

import java.util.ArrayList;
import java.util.List;

import app.meethalfway.meetings.domain.model.DomainConstants;
import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EvaluatedCandidate;

/**
 * Selects the {@code Fairest_Strategy} winner: among the candidates whose total
 * travel time stays within the {@code Efficiency_Tolerance} of the optimum, the
 * candidate that minimizes the spread of travel times {@code Std_Dev (σ)}
 * (Requirement 4.4).
 *
 * <p>Unlike Fastest and Minimax, this selector first restricts the candidate set
 * to the <em>efficiency-feasible</em> subset before applying its tie-break chain
 * (Requirements 4.1, 4.2, 4.3):
 * <ol>
 *   <li>{@code T*} — the {@code Fastest_Optimum}, the minimum {@code Sum_Time (Σ)}
 *       across all candidates (Req 4.1).</li>
 *   <li>feasible set — every candidate whose {@code Σ} is within the fixed 15%
 *       {@code Efficiency_Tolerance} of {@code T*}, i.e.
 *       {@code Σ ≤ (1 + EFFICIENCY_TOLERANCE) · T*} (Req 4.2). The tolerance is a
 *       fixed domain constant, {@link DomainConstants#EFFICIENCY_TOLERANCE},
 *       never a config field (Req 4.3).</li>
 * </ol>
 *
 * <p><b>Exact feasibility boundary.</b> A candidate is feasible when
 * {@code Σ ≤ (1 + EFFICIENCY_TOLERANCE) · T* + ε}, where {@code ε} is the
 * tie-breaker's {@code Equality_Threshold}. The {@code + ε} slack keeps the
 * boundary consistent with the ε-tolerant comparisons used everywhere else in
 * the engine and matches Property 7, which asserts the Fairest {@code Σ} is
 * {@code ≤ 1.15 · T*} <em>within ε</em>. The feasible set is therefore never
 * empty: the candidate achieving {@code T*} always satisfies
 * {@code Σ = T* ≤ (1 + EFFICIENCY_TOLERANCE) · T* + ε} (the tolerance is
 * non-negative and {@code T*} is finite and non-negative).
 *
 * <p>Among the feasible set, ties are resolved through the deterministic,
 * ε-tolerant key order mandated by Requirement 4:
 * <ol>
 *   <li>{@code σ} — lower {@code Std_Dev} wins (Req 4.4).</li>
 *   <li>{@code Σ} — on a {@code σ} tie within ε, lower {@code Sum_Time} wins
 *       (Req 4.5).</li>
 *   <li>{@code max} — on a further {@code Σ} tie within ε, lower {@code Max_Time}
 *       wins (Req 4.6).</li>
 *   <li>{@code distance-to-centroid} — on a further {@code max} tie within ε, the
 *       candidate closest to the {@code Geographic_Centroid} wins, compared
 *       without ε so the result is always a single, deterministic winner
 *       (Req 4.7).</li>
 * </ol>
 *
 * <p>The ε tolerance and the total-order composition are delegated to the
 * injected {@link TieBreaker}; this selector adds the feasibility restriction and
 * fixes the strategy-specific priority order of the metric comparators. The
 * {@code Geographic_Centroid} is supplied by the caller via
 * {@link #select(List, Coordinate)}.
 *
 * <p>This is framework-free domain logic with no dependency on HTTP, persistence,
 * or any provider.
 */
public final class FairestSelector implements StrategySelector {

    private final TieBreaker tieBreaker;

    /**
     * Creates a Fairest selector backed by the given tie-breaker. The ε tolerance
     * is carried by the {@link TieBreaker} itself (from {@code EngineConfig}); the
     * {@code Efficiency_Tolerance} is the fixed
     * {@link DomainConstants#EFFICIENCY_TOLERANCE}.
     *
     * @param tieBreaker the shared tie-breaker used to build the ε-tolerant
     *                  comparator chain and the feasibility slack; must not be null
     * @throws IllegalArgumentException if {@code tieBreaker} is null
     */
    public FairestSelector(TieBreaker tieBreaker) {
        if (tieBreaker == null) {
            throw new IllegalArgumentException("tieBreaker must not be null");
        }
        this.tieBreaker = tieBreaker;
    }

    /**
     * Selects the Fairest winner: restrict to the efficiency-feasible subset
     * ({@code Σ ≤ (1 + EFFICIENCY_TOLERANCE) · T* + ε}), then apply the key order
     * {@code σ → Σ → max → distance-to-centroid}.
     *
     * @param candidates the evaluated candidates; must be non-null and non-empty
     * @param centroid   the {@code Geographic_Centroid} for the final tiebreak;
     *                  must not be null
     * @return the single Fairest-strategy winner
     * @throws IllegalArgumentException if {@code candidates} is null/empty or
     *                                  {@code centroid} is null
     */
    @Override
    public EvaluatedCandidate select(List<EvaluatedCandidate> candidates, Coordinate centroid) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must not be null or empty");
        }
        if (centroid == null) {
            throw new IllegalArgumentException("centroid must not be null");
        }

        List<EvaluatedCandidate> feasible = feasibleSet(candidates);

        return tieBreaker.selectWinner(
                feasible,
                tieBreaker.totalOrder(
                        centroid,
                        List.of(
                                tieBreaker.byStdDev(),
                                tieBreaker.bySumTime(),
                                tieBreaker.byMaxTime())));
    }

    /**
     * Computes the efficiency-feasible subset of {@code candidates}: those whose
     * {@code Sum_Time} is within the {@code Efficiency_Tolerance} (plus ε slack) of
     * the {@code Fastest_Optimum T*}. This set is never empty — the candidate
     * achieving {@code T*} is always included.
     */
    private List<EvaluatedCandidate> feasibleSet(List<EvaluatedCandidate> candidates) {
        double tStar = Double.POSITIVE_INFINITY;
        for (EvaluatedCandidate candidate : candidates) {
            if (candidate.sumTime() < tStar) {
                tStar = candidate.sumTime();
            }
        }

        double threshold =
                (1.0 + DomainConstants.EFFICIENCY_TOLERANCE) * tStar + tieBreaker.epsilonMinutes();

        List<EvaluatedCandidate> feasible = new ArrayList<>();
        for (EvaluatedCandidate candidate : candidates) {
            if (candidate.sumTime() <= threshold) {
                feasible.add(candidate);
            }
        }
        return feasible;
    }
}
