package app.meethalfway.domain.engine;

import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.EvaluatedCandidate;

/**
 * Builds the deterministic, ε-tolerant comparator chains that each strategy
 * selector uses to pick a single winning candidate (Requirements 2.5, 3.4, 4.7;
 * Property 9).
 *
 * <p><b>ε-tolerant equality.</b> Two metric values are considered tied when
 * {@code areEqual(a, b) := |a − b| < ε}, where {@code ε} is
 * {@code config.epsilonMinutes} (≈ 0.5 min). A comparator built from a metric
 * treats any two candidates within ε on that metric as equal, so the comparison
 * "falls through" to the next comparator in the chain. This is what makes the
 * tie-break sub-criteria (e.g. Fastest: {@code Σ → max → σ}) behave as the
 * requirements describe: candidates that are effectively equal on the primary
 * metric are ranked by the secondary metric, and so on.
 *
 * <p><b>Total order.</b> Any residual tie after all metric comparators is broken
 * in two non-ε steps: first {@link #centroidDistance(Coordinate)} compares the
 * (squared) distance to the {@code Geographic_Centroid}, then {@link #exactPoint()}
 * compares the candidate point by exact {@code lat} then {@code lng}. The
 * centroid distance alone is not a total order — two <em>distinct</em> points can
 * be equidistant from the centroid (e.g. symmetric about it) and stay tied — so
 * the exact lat/lng comparator is required to strictly order that case. Together
 * they make the chain a genuine total order that always yields a single selected
 * candidate with no unresolved tie for distinct points; two truly identical
 * points remain interchangeable, which is acceptable (Property 9).
 *
 * <p><b>Composability for selectors.</b> Each selector (tasks 5.2–5.4) builds its
 * chain by ordering the ε-tolerant metric comparators and finishing with the
 * centroid comparator then the exact lat/lng comparator:
 * <ul>
 *   <li>Fastest: {@code Σ → max → σ → distance → lat/lng}</li>
 *   <li>Minimax: {@code max → Σ → σ → distance → lat/lng}</li>
 *   <li>Fairest: {@code σ → Σ → max → distance → lat/lng}</li>
 * </ul>
 * The helpers {@link #bySumTime()}, {@link #byMaxTime()}, and {@link #byStdDev()}
 * expose the three metric comparators; {@link #totalOrder(Coordinate, List)}
 * composes any ordering of them and appends the centroid then exact-point
 * tiebreaks.
 *
 * <p>This is framework-free domain logic with no dependency on HTTP, persistence,
 * or any provider.
 */
public final class TieBreaker {

    private final double epsilonMinutes;

    /**
     * Creates a tie-breaker whose metric comparisons use the given ε tolerance.
     *
     * @param epsilonMinutes the {@code Equality_Threshold} ε in minutes; must be
     *                       finite and strictly positive (matching
     *                       {@code EngineConfig.epsilonMinutes})
     * @throws IllegalArgumentException if {@code epsilonMinutes} is non-finite or
     *                                  not strictly positive
     */
    public TieBreaker(double epsilonMinutes) {
        if (!Double.isFinite(epsilonMinutes) || epsilonMinutes <= 0.0) {
            throw new IllegalArgumentException(
                    "epsilonMinutes must be a finite, strictly-positive number, was: " + epsilonMinutes);
        }
        this.epsilonMinutes = epsilonMinutes;
    }

    /** The ε tolerance in minutes used by this tie-breaker's metric comparisons. */
    public double epsilonMinutes() {
        return epsilonMinutes;
    }

    /**
     * ε-tolerant equality: {@code |a − b| < ε}. Two metric values within ε are
     * treated as tied.
     *
     * @param a first value
     * @param b second value
     * @return {@code true} when the values differ by strictly less than ε
     */
    public boolean areEqual(double a, double b) {
        return Math.abs(a - b) < epsilonMinutes;
    }

    /**
     * An ε-tolerant comparator over an arbitrary numeric metric of an
     * {@link EvaluatedCandidate}: values within ε compare as equal (returns 0),
     * so the chain falls through to the next comparator; otherwise the lower value
     * sorts first.
     *
     * @param metric extracts the metric value from a candidate; must not be null
     * @return an ε-tolerant ascending comparator on that metric
     * @throws IllegalArgumentException if {@code metric} is null
     */
    public Comparator<EvaluatedCandidate> byMetric(ToDoubleFunction<EvaluatedCandidate> metric) {
        if (metric == null) {
            throw new IllegalArgumentException("metric must not be null");
        }
        return (left, right) -> {
            double a = metric.applyAsDouble(left);
            double b = metric.applyAsDouble(right);
            if (areEqual(a, b)) {
                return 0;
            }
            return Double.compare(a, b);
        };
    }

    /** ε-tolerant ascending comparator on {@code Sum_Time} (Σ tᵢ). */
    public Comparator<EvaluatedCandidate> bySumTime() {
        return byMetric(EvaluatedCandidate::sumTime);
    }

    /** ε-tolerant ascending comparator on {@code Max_Time} (max tᵢ). */
    public Comparator<EvaluatedCandidate> byMaxTime() {
        return byMetric(candidate -> candidate.maxTime());
    }

    /** ε-tolerant ascending comparator on {@code Std_Dev} (σ). */
    public Comparator<EvaluatedCandidate> byStdDev() {
        return byMetric(EvaluatedCandidate::stdDev);
    }

    /**
     * The centroid tie-break comparator: distance to the
     * {@code Geographic_Centroid}, compared <em>without</em> ε so it orders a
     * residual metric tie (Requirements 2.5, 3.4, 4.7; Property 9). Uses squared
     * distance, which preserves the distance ordering.
     *
     * <p>Note that distance-to-centroid alone is <em>not</em> a total order over
     * distinct points: two distinct candidates can be equidistant from the
     * centroid (e.g. symmetric about it), which leaves them tied. The final,
     * strict ordering is provided by {@link #exactPoint()}, which
     * {@link #totalOrder(Coordinate, List)} appends after this comparator.
     *
     * @param centroid the geographic centroid to measure distance to; must not be
     *                 null
     * @return an ascending comparator on distance-to-centroid, applied without ε
     * @throws IllegalArgumentException if {@code centroid} is null
     */
    public Comparator<EvaluatedCandidate> centroidDistance(Coordinate centroid) {
        if (centroid == null) {
            throw new IllegalArgumentException("centroid must not be null");
        }
        return Comparator.comparingDouble(
                candidate -> GeographicCentroid.squaredDistance(candidate.point(), centroid));
    }

    /**
     * The final, absolute tie-break comparator: the candidate's point compared
     * lexicographically by exact {@code lat} then {@code lng}, <em>without</em> ε.
     * Because any two <em>distinct</em> points differ in at least one of their
     * exact latitude/longitude values, this comparator strictly orders them, and
     * two truly identical points compare as equal (they are interchangeable). It
     * is what makes the composed chain a genuine total order over distinct points
     * — in particular it resolves the equidistant-but-distinct case that
     * {@link #centroidDistance(Coordinate)} leaves tied (Property 9).
     *
     * @return an ascending comparator on the candidate point by exact lat then lng
     */
    public Comparator<EvaluatedCandidate> exactPoint() {
        return Comparator
                .comparingDouble((EvaluatedCandidate candidate) -> candidate.point().lat())
                .thenComparingDouble(candidate -> candidate.point().lng());
    }

    /**
     * Composes {@code metricComparators} in order and appends the non-ε
     * tie-breaks — first {@link #centroidDistance(Coordinate)}, then the exact
     * {@link #exactPoint()} lat/lng comparator — producing a total-order
     * comparator suitable for selecting a single winner. Selectors pass their
     * strategy-specific ordering of the metric comparators (e.g. Fastest passes
     * {@code [bySumTime(), byMaxTime(), byStdDev()]}).
     *
     * <p>Distance-to-centroid narrows a residual metric tie but does not, on its
     * own, order two distinct points that happen to be equidistant from the
     * centroid; the appended exact lat/lng comparator resolves exactly that case,
     * so the composed chain is a genuine total order over distinct points and
     * always yields a single winner regardless of input order (Property 9). Two
     * truly identical points remain interchangeable, which is acceptable.
     *
     * @param centroid          the geographic centroid for the centroid tiebreak;
     *                          must not be null
     * @param metricComparators the ε-tolerant metric comparators in priority
     *                          order; must be non-null and non-empty with no null
     *                          element
     * @return a total-order comparator ending in centroid-distance then exact
     *         lat/lng
     * @throws IllegalArgumentException if any argument is null/empty or contains a
     *                                  null comparator
     */
    public Comparator<EvaluatedCandidate> totalOrder(
            Coordinate centroid, List<Comparator<EvaluatedCandidate>> metricComparators) {
        if (centroid == null) {
            throw new IllegalArgumentException("centroid must not be null");
        }
        if (metricComparators == null || metricComparators.isEmpty()) {
            throw new IllegalArgumentException("metricComparators must not be null or empty");
        }

        Comparator<EvaluatedCandidate> first = metricComparators.get(0);
        if (first == null) {
            throw new IllegalArgumentException("metricComparators must not contain a null comparator");
        }
        Comparator<EvaluatedCandidate> chain = first;
        for (int i = 1; i < metricComparators.size(); i++) {
            Comparator<EvaluatedCandidate> comparator = metricComparators.get(i);
            if (comparator == null) {
                throw new IllegalArgumentException("metricComparators must not contain a null comparator");
            }
            chain = chain.thenComparing(comparator);
        }
        return chain.thenComparing(centroidDistance(centroid)).thenComparing(exactPoint());
    }

    /**
     * Selects the single winning candidate from {@code candidates} using the
     * given total-order comparator. Returns the strict minimum; because the
     * comparator ends in the non-ε centroid tiebreak followed by the exact
     * lat/lng point tiebreak, the winner is unambiguous whenever candidate points
     * are distinct (Property 9).
     *
     * @param candidates the candidates to choose from; must be non-null and
     *                  non-empty
     * @param order     the total-order comparator (typically from
     *                  {@link #totalOrder}); must not be null
     * @return the single winning candidate
     * @throws IllegalArgumentException if {@code candidates} is null/empty or
     *                                  {@code order} is null
     */
    public EvaluatedCandidate selectWinner(
            List<EvaluatedCandidate> candidates, Comparator<EvaluatedCandidate> order) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must not be null or empty");
        }
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }

        EvaluatedCandidate best = candidates.get(0);
        for (int i = 1; i < candidates.size(); i++) {
            EvaluatedCandidate current = candidates.get(i);
            if (order.compare(current, best) < 0) {
                best = current;
            }
        }
        return best;
    }
}
