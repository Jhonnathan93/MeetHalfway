package app.meethalfway.meetings.domain.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import app.meethalfway.shared.domain.ParticipantId;

/**
 * The including-versus-excluding comparison presented when a meeting contains at
 * least one Outlier (Requirement 7).
 *
 * <p>When outliers are detected (7.1), the engine computes all three strategies
 * twice — once including the outlier(s) and once excluding them — and returns
 * both sets along with the group average travel time for each (7.2, 7.3). The
 * Creator decides whether to include or exclude the outlier(s) (7.4); the engine
 * never drops an outlier silently (7.5), which is why both computations are
 * always carried together in this record.
 *
 * <p>Invariants enforced at construction:
 * <ul>
 *   <li>{@code outliers} is non-null, non-empty (this record only exists when at
 *       least one outlier was detected), and is defensively copied into an
 *       unmodifiable set.</li>
 *   <li>{@code including} and {@code excluding} are non-null.</li>
 *   <li>Both average travel times are finite and non-negative (they are averages
 *       of whole-minute travel times, Requirement 8.2).</li>
 * </ul>
 *
 * @param outliers                the participants flagged as outliers (7.1)
 * @param including               the three strategy results computed with the
 *                                outlier(s) included (7.2)
 * @param excluding               the three strategy results computed with the
 *                                outlier(s) excluded (7.2)
 * @param avgTravelTimeIncluding  group average travel time for the including
 *                                computation (7.3)
 * @param avgTravelTimeExcluding  group average travel time for the excluding
 *                                computation (7.3)
 */
public record OutlierTradeoff(
        Set<ParticipantId> outliers,
        StrategyResults including,
        StrategyResults excluding,
        double avgTravelTimeIncluding,
        double avgTravelTimeExcluding) {

    public OutlierTradeoff {
        if (outliers == null) {
            throw new IllegalArgumentException("outliers must not be null");
        }
        if (outliers.isEmpty()) {
            throw new IllegalArgumentException("outliers must not be empty for an outlier trade-off");
        }
        if (outliers.contains(null)) {
            throw new IllegalArgumentException("outliers must not contain a null id");
        }
        if (including == null) {
            throw new IllegalArgumentException("including results must not be null");
        }
        if (excluding == null) {
            throw new IllegalArgumentException("excluding results must not be null");
        }
        if (!Double.isFinite(avgTravelTimeIncluding) || avgTravelTimeIncluding < 0.0) {
            throw new IllegalArgumentException(
                    "avgTravelTimeIncluding must be a finite, non-negative number, was: " + avgTravelTimeIncluding);
        }
        if (!Double.isFinite(avgTravelTimeExcluding) || avgTravelTimeExcluding < 0.0) {
            throw new IllegalArgumentException(
                    "avgTravelTimeExcluding must be a finite, non-negative number, was: " + avgTravelTimeExcluding);
        }
        outliers = Collections.unmodifiableSet(new LinkedHashSet<>(outliers));
    }
}
