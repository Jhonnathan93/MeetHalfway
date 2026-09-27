package app.meethalfway.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The final result for a single strategy (Fastest, Minimax, or Fairest).
 *
 * <p>Requirement 8 mandates that each strategy return exactly one
 * {@link Coordinate} point (8.1, 8.3) together with the per-participant travel
 * time and the aggregate metrics {@code Sum_Time}, {@code Max_Time}, and
 * {@code Std_Dev} (8.2). This record is the immutable carrier of that result;
 * it mirrors {@link EvaluatedCandidate} but represents the chosen winner rather
 * than one of many candidates.
 *
 * <p>Invariants enforced at construction:
 * <ul>
 *   <li>{@code point} and {@code perParticipant} are non-null; the map is
 *       defensively copied and made unmodifiable.</li>
 *   <li>{@code perParticipant} is non-empty.</li>
 *   <li>{@code sumTime}, {@code maxTime}, and {@code stdDev} are finite and
 *       non-negative (Requirement 8.2).</li>
 * </ul>
 *
 * @param point         the selected meeting-point coordinate (Requirement 8.3)
 * @param perParticipant per-participant travel time (Requirement 8.2)
 * @param sumTime       {@code Σ tᵢ} (Requirement 8.2)
 * @param maxTime       {@code max tᵢ} (Requirement 8.2)
 * @param stdDev        {@code σ(t)} (Requirement 8.2)
 */
public record StrategyResult(
        Coordinate point,
        Map<ParticipantId, Minutes> perParticipant,
        double sumTime,
        int maxTime,
        double stdDev) {

    public StrategyResult {
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
        perParticipant = Collections.unmodifiableMap(new LinkedHashMap<>(perParticipant));
    }
}
