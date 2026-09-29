package app.meethalfway.meetings.domain.engine;

import app.meethalfway.meetings.domain.model.StrategyResult;
import app.meethalfway.shared.domain.Coordinate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Selects a candidate using ordered epsilon ties and deterministic spatial tie-breaks. */
public final class TieBreaker {

    private final double epsilonMinutes;

    public TieBreaker(double epsilonMinutes) {
        if (!Double.isFinite(epsilonMinutes) || epsilonMinutes <= 0.0) {
            throw new IllegalArgumentException(
                    "epsilonMinutes must be a finite, strictly-positive number, was: " + epsilonMinutes);
        }
        this.epsilonMinutes = epsilonMinutes;
    }

    public double epsilonMinutes() {
        return epsilonMinutes;
    }

    /** Two finite metrics tie when their absolute difference is strictly below epsilon. */
    public boolean areEqual(double left, double right) {
        return Math.abs(left - right) < epsilonMinutes;
    }

    /**
     * Applies each metric priority against that stage's minimum, retaining values
     * strictly within epsilon of it before considering the next metric. This
     * defines ties independently of candidate order; pairwise epsilon comparators
     * are not transitive and can otherwise make selection order-dependent.
     */
    public StrategyResult selectWinner(
            List<StrategyResult> candidates,
            Coordinate centroid,
            List<ToDoubleFunction<StrategyResult>> metricPriorities) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must be non-empty");
        }
        for (StrategyResult candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("candidates must not contain null values");
            }
        }
        if (centroid == null) {
            throw new IllegalArgumentException("centroid must not be null");
        }
        if (metricPriorities == null || metricPriorities.isEmpty()) {
            throw new IllegalArgumentException("metricPriorities must be non-empty");
        }
        for (ToDoubleFunction<StrategyResult> metric : metricPriorities) {
            if (metric == null) {
                throw new IllegalArgumentException("metricPriorities must not contain null values");
            }
        }

        List<StrategyResult> remaining = new ArrayList<>(candidates);
        for (ToDoubleFunction<StrategyResult> metric : metricPriorities) {
            double minimum = remaining.stream().mapToDouble(metric).min().orElseThrow();
            remaining.removeIf(candidate -> !areEqual(metric.applyAsDouble(candidate), minimum));
        }

        Comparator<StrategyResult> spatialOrder = Comparator
                .comparingDouble((StrategyResult candidate) ->
                        GeographicCentroid.squaredDistance(candidate.point(), centroid))
                .thenComparingDouble(candidate -> candidate.point().lat())
                .thenComparingDouble(candidate -> candidate.point().lng());
        return remaining.stream().min(spatialOrder).orElseThrow();
    }
}
