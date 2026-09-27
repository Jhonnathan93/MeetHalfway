package app.meethalfway.domain.model;

/**
 * The configured rule used to decide whether a participant is an Outlier
 * (Requirement 7.1). The rule is <em>config-driven</em>, never hard-coded: the
 * exact multiplier/percentile is pending calibration with real usage data (see
 * the requirements' Open Questions), so it is carried here as a value on
 * {@link EngineConfig} rather than embedded in the detection logic.
 *
 * <p>Modelled as a sealed algebraic type so a caller must handle each supported
 * form explicitly. There are exactly two MVP variants:
 * <ul>
 *   <li>{@link MedianMultiple} — a participant is an outlier when their travel
 *       time exceeds {@code k * median(times)} (candidate {@code k ≈ 2}).</li>
 *   <li>{@link Percentile} — a participant is an outlier when their travel time
 *       exceeds the given percentile of the group's travel times (e.g. p90).</li>
 * </ul>
 */
public sealed interface OutlierRule permits OutlierRule.MedianMultiple, OutlierRule.Percentile {

    /**
     * A participant is flagged when {@code t_i > k * median(t)}.
     *
     * @param k the strictly-positive median multiple (candidate {@code ≈ 2},
     *          pending calibration)
     */
    record MedianMultiple(double k) implements OutlierRule {

        public MedianMultiple {
            if (Double.isNaN(k) || Double.isInfinite(k)) {
                throw new IllegalArgumentException("k must be a finite number, was: " + k);
            }
            if (k <= 0.0) {
                throw new IllegalArgumentException("k must be strictly positive, was: " + k);
            }
        }
    }

    /**
     * A participant is flagged when their travel time exceeds the {@code p}th
     * percentile of the group's travel times.
     *
     * @param p the percentile in the exclusive-inclusive range {@code (0, 100]}
     *          (e.g. {@code 90.0} for p90)
     */
    record Percentile(double p) implements OutlierRule {

        public Percentile {
            if (Double.isNaN(p) || Double.isInfinite(p)) {
                throw new IllegalArgumentException("p must be a finite number, was: " + p);
            }
            if (p <= 0.0 || p > 100.0) {
                throw new IllegalArgumentException("p must be within (0, 100], was: " + p);
            }
        }
    }
}
