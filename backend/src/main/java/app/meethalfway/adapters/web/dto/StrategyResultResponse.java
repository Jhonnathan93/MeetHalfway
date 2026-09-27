package app.meethalfway.adapters.web.dto;

import java.util.Map;

/**
 * Wire representation of a single strategy result (Fastest, Minimax, or
 * Fairest) &mdash; one selected point plus its metrics (Requirement 8).
 *
 * <p>{@code perParticipant} maps each participant id to that participant's
 * travel time in whole minutes. JSON fields are camelCase.
 *
 * @param point          the selected meeting-point coordinate
 * @param perParticipant participant id &rarr; travel time in minutes
 * @param sumTime        {@code Σ tᵢ}
 * @param maxTime        {@code max tᵢ}
 * @param stdDev         {@code σ(t)}
 */
public record StrategyResultResponse(
        CoordinateResponse point,
        Map<String, Integer> perParticipant,
        double sumTime,
        int maxTime,
        double stdDev) {
}
