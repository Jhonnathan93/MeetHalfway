package app.meethalfway.meetings.domain.engine;

import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.shared.domain.ParticipantId;
import java.util.Map;
import java.util.Set;

/**
 * Flags participants whose travel time makes them an Outlier under the
 * configured rule (Requirement 7.1).
 *
 * <p>The detector is <em>config-driven</em>: it reads the threshold from
 * {@link EngineConfig#outlierRule()} and never hard-codes a multiplier or
 * percentile. Its output is exactly the set of participants selected by applying
 * that rule to the supplied travel-time vector, which is what
 * <em>Property 14 — outlier detection matches the configured rule</em> asserts.
 *
 * <p>Detection is a pure function of its inputs: the same travel-time map and
 * config always yield the same outlier set, with no reliance on iteration order.
 */
public interface OutlierDetector {

    /**
     * Returns the participants flagged as outliers by the configured rule.
     *
     * @param travelTimes per-participant travel time for a reference computation;
     *                    must be non-null (an empty map yields an empty result)
     * @param config      the engine configuration carrying the outlier rule; must
     *                    be non-null
     * @return the exact set of outlier participant ids; empty when no participant
     *         exceeds the configured threshold
     */
    Set<ParticipantId> detect(Map<ParticipantId, Minutes> travelTimes, EngineConfig config);
}
