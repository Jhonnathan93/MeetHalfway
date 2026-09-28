package app.meethalfway.meetings.web.dto;

import app.meethalfway.shared.web.dto.CoordinateResponse;
import java.util.Map;

/**
 * Wire representation of a single strategy result (Fastest, Minimax, or
 * Fairest) &mdash; one selected point plus its metrics (Requirement 8).
 *
 * <p>{@code perParticipant} maps each participant id to that participant's
 * travel time in whole minutes. JSON fields are camelCase.
 *
 * <p><strong>Additive display metadata (Requirements 10.2, 10.4, 10.8, 12.4):</strong>
 * {@code candidateId} and {@code recommended} were added to let the frontend
 * display and distinguish candidate points without recomputing anything. They are
 * appended after the existing components; no existing field was removed or
 * renamed, so the JSON contract remains backward-compatible.
 *
 * @param point          the selected meeting-point coordinate
 * @param perParticipant participant id &rarr; travel time in minutes
 * @param sumTime        {@code Σ tᵢ}
 * @param maxTime        {@code max tᵢ}
 * @param stdDev         {@code σ(t)}
 * @param candidateId    the stable strategy key uniquely mapping this point to its
 *                       strategy: {@code "fastest"}, {@code "minimax"}, or
 *                       {@code "fairest"} (Requirement 10.2)
 * @param recommended    {@code true} for the one recommended point per strategy so
 *                       the frontend can distinguish it from alternatives
 *                       (Requirement 10.4)
 */
public record StrategyResultResponse(
        CoordinateResponse point,
        Map<String, Integer> perParticipant,
        double sumTime,
        int maxTime,
        double stdDev,
        String candidateId,
        boolean recommended) {
}
