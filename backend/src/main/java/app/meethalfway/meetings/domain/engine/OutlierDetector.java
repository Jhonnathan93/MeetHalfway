package app.meethalfway.meetings.domain.engine;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.meetings.domain.model.OutlierRule;
import app.meethalfway.shared.domain.ParticipantId;

/**
 * Config-driven {@link OutlierDetector} that applies the
 * {@link OutlierRule} carried by {@link EngineConfig} to a vector of
 * per-participant travel times (Requirement 7.1).
 *
 * <p>A participant is flagged when their travel time <em>strictly exceeds</em>
 * the rule's threshold ({@code t_i > threshold}); a participant sitting exactly
 * on the threshold is not an outlier. This matches the design wording ("exceeds")
 * and makes the boundary well-defined for <em>Property 14</em>.
 *
 * <h2>Threshold definitions</h2>
 * The statistic is computed over the multiset of travel-time values (one entry
 * per participant, duplicates included), using the median definition below:
 *
 * Threshold is {@code k * median}. The <em>median</em> uses the standard
 * average-of-two-middle method on the ascending-sorted values {@code v}:
 * <ul>
 *   <li>odd {@code n}: {@code median = v[n / 2]};</li>
 *   <li>even {@code n}: {@code median = (v[n / 2 - 1] + v[n / 2]) / 2.0}.</li>
 * </ul>
 *
 * <p>An empty travel-time map has no statistic to compute against and yields an
 * empty outlier set.
 */
public final class OutlierDetector {

    public Set<ParticipantId> detect(Map<ParticipantId, Minutes> travelTimes, EngineConfig config) {
        Objects.requireNonNull(travelTimes, "travelTimes must not be null");
        Objects.requireNonNull(config, "config must not be null");

        if (travelTimes.isEmpty()) {
            return Set.of();
        }

        double threshold = thresholdFor(config.outlierRule(), sortedValues(travelTimes));

        // Preserve the caller's iteration order for a stable, reproducible result.
        Set<ParticipantId> outliers = new LinkedHashSet<>();
        for (Map.Entry<ParticipantId, Minutes> entry : travelTimes.entrySet()) {
            if (entry.getValue().value() > threshold) {
                outliers.add(entry.getKey());
            }
        }
        return outliers;
    }

    private static double[] sortedValues(Map<ParticipantId, Minutes> travelTimes) {
        double[] values = travelTimes.values().stream()
                .mapToDouble(minutes -> minutes.value())
                .toArray();
        Arrays.sort(values);
        return values;
    }

    private static double thresholdFor(OutlierRule rule, double[] sortedValues) {
        return rule.k() * median(sortedValues);
    }

    /**
     * Median with the average-of-two-middle method. Assumes {@code sorted} is
     * non-empty and ascending.
     */
    private static double median(double[] sorted) {
        int n = sorted.length;
        int mid = n / 2;
        if (n % 2 == 1) {
            return sorted[mid];
        }
        return (sorted[mid - 1] + sorted[mid]) / 2.0;
    }

}
