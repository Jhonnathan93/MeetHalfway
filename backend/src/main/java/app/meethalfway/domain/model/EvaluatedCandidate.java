package app.meethalfway.domain.model;

import java.util.Collections;
import java.util.Map;

/**
 * A candidate meeting point together with the metrics computed from every
 * participant's travel time to it.
 *
 * <p>The engine evaluates many candidates (one per Grid_Search point) and each
 * strategy selector picks the winner among these evaluated candidates. The
 * metrics are derived from {@code perParticipant} (Requirement 5.2):
 *
 * <ul>
 *   <li>{@code sumTime} — {@code Σ tᵢ}, the arithmetic sum of travel times.</li>
 *   <li>{@code maxTime} — {@code max tᵢ}, the largest single travel time (whole
 *       minutes, so an {@code int}).</li>
 *   <li>{@code stdDev} — {@code σ(t)}, the standard deviation of travel times.</li>
 * </ul>
 *
 * <p>Invariants enforced at construction:
 * <ul>
 *   <li>{@code point} and {@code perParticipant} are non-null; the map is
 *       defensively copied and made unmodifiable so the record stays immutable.</li>
 *   <li>{@code perParticipant} is non-empty — a candidate is meaningless without
 *       at least one participant travel time.</li>
 *   <li>{@code sumTime}, {@code maxTime}, and {@code stdDev} are finite and
 *       non-negative (travel time is a non-negative whole-minute quantity,
 *       Requirement 8.2).</li>
 * </ul>
 *
 * @param point         the evaluated meeting-point coordinate
 * @param perParticipant travel time from each participant to {@code point}
 * @param sumTime       {@code Σ tᵢ}
 * @param maxTime       {@code max tᵢ}
 * @param stdDev        {@code σ(t)}
 */
public record EvaluatedCandidate(
        Coordinate point,
        Map<ParticipantId, Minutes> perParticipant,
        double sumTime,
        int maxTime,
        double stdDev) {

    public EvaluatedCandidate {
        if (point == null) {
            throw new IllegalArgumentException("point must not be null");
        }
        if (perParticipant == null) {
            throw new IllegalArgumentException("perParticipant must not be null");
        }
        if (perParticipant.isEmpty()) {
            throw new IllegalArgumentException("perParticipant must not be empty");
        }
        if (perParticipant.containsKey(null) || perParticipant.containsValue(null)) {
            throw new IllegalArgumentException("perParticipant must not contain null keys or values");
        }
        if (!Double.isFinite(sumTime) || sumTime < 0.0) {
            throw new IllegalArgumentException("sumTime must be a finite, non-negative number, was: " + sumTime);
        }
        if (maxTime < 0) {
            throw new IllegalArgumentException("maxTime must be non-negative, was: " + maxTime);
        }
        if (!Double.isFinite(stdDev) || stdDev < 0.0) {
            throw new IllegalArgumentException("stdDev must be a finite, non-negative number, was: " + stdDev);
        }
        perParticipant = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(perParticipant));
    }
}
