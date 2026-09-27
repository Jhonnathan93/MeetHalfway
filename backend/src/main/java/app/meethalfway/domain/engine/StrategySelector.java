package app.meethalfway.domain.engine;

import java.util.List;

import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.EvaluatedCandidate;

/**
 * Selects the single winning {@link EvaluatedCandidate} for one optimization
 * strategy (Fastest, Minimax, or Fairest).
 *
 * <p>Every strategy shares the same contract: given the fully evaluated
 * candidates — each already carrying its {@code Sum_Time}, {@code Max_Time}, and
 * {@code Std_Dev} metrics — the selector applies that strategy's deterministic,
 * ε-tolerant tie-break chain and returns exactly one candidate (Requirement 8.1).
 * The three concrete selectors differ only in the priority order of their metric
 * comparators; all of them share a common {@link TieBreaker} and end their chain
 * with two non-ε tiebreaks — distance to the {@code Geographic_Centroid} followed
 * by the exact {@code lat} then {@code lng} of the candidate point. Distance to
 * the centroid narrows a residual tie but cannot order two distinct points that
 * are equidistant from it; the exact lat/lng comparator resolves that case, so
 * the chain is a genuine total order over distinct points and yields a single
 * unambiguous winner regardless of input order (Requirements 2.5, 3.4, 4.7;
 * Property 9):
 *
 * <ul>
 *   <li>{@code FastestSelector} — {@code Σ → max → σ → distance → lat/lng}</li>
 *   <li>{@code MinimaxSelector} — {@code max → Σ → σ → distance → lat/lng}</li>
 *   <li>{@code FairestSelector} — {@code σ → Σ → max → distance → lat/lng} over
 *       the efficiency-feasible subset</li>
 * </ul>
 *
 * <p>The {@code centroid} is the {@code Geographic_Centroid} of the participant
 * origins. It is computed once by the caller (from the participant locations, not
 * from the candidates) and passed in, so the same centroid is used consistently
 * across all strategies for a given meeting.
 *
 * <p>This is framework-free domain logic with no dependency on HTTP, persistence,
 * or any provider.
 */
public interface StrategySelector {

    /**
     * Selects the single winning candidate for this strategy.
     *
     * @param candidates the fully evaluated candidates to choose from; must be
     *                  non-null and non-empty
     * @param centroid  the {@code Geographic_Centroid} of the participant origins,
     *                  used as the final tie-breaker; must not be null
     * @return the single winning candidate for this strategy
     * @throws IllegalArgumentException if {@code candidates} is null/empty or
     *                                  {@code centroid} is null
     */
    EvaluatedCandidate select(List<EvaluatedCandidate> candidates, Coordinate centroid);
}
