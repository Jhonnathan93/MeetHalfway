package app.meethalfway.meetings.domain.engine;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EvaluatedCandidate;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.shared.domain.ParticipantId;

import java.util.Map;

/**
 * Computes the three optimization metrics for a candidate meeting point from the
 * real per-participant travel times to that point (Requirement 5.2), and packages
 * them into an {@link EvaluatedCandidate}.
 *
 * <p>Every metric is derived exclusively from measured {@code Travel_Time}s — the
 * engine never selects a point by geographic center (Requirement 5.3). This class
 * is framework-free domain logic with no dependency on HTTP, persistence, or any
 * provider; it merely reads the travel-time map handed to it.
 *
 * <h2>Metric definitions</h2>
 * <p>Given the per-participant travel times {@code t₁, t₂, …, tₙ} (whole minutes,
 * {@code n ≥ 1}):
 * <ul>
 *   <li><b>Sum_Time</b> — {@code Σ tᵢ}, the arithmetic sum of the travel times,
 *       returned as a {@code double} so downstream comparisons stay exact.</li>
 *   <li><b>Max_Time</b> — {@code max tᵢ}, the largest single travel time (whole
 *       minutes, an {@code int}).</li>
 *   <li><b>Std_Dev</b> — the <b>population</b> standard deviation
 *       {@code σ = sqrt( (1/n) · Σ (tᵢ − μ)² )}, where {@code μ = (Σ tᵢ) / n}.
 *       <p><b>Population, not sample:</b> we divide by {@code n} (not
 *       {@code n − 1}). The travel-time vector for a meeting is the <em>entire</em>
 *       group, not a sample drawn from a larger population, so the population
 *       formula is the correct dispersion measure. It is also defined for a
 *       single participant ({@code n = 1} yields {@code σ = 0}), whereas the
 *       sample formula would divide by zero. Property 4 (task 4.4) must assert
 *       {@code Std_Dev} against this same population definition.</li>
 * </ul>
 */
public final class MetricCalculator {

    /**
     * Builds an {@link EvaluatedCandidate} for {@code point} from the travel times
     * each participant needs to reach it, computing {@code Sum_Time},
     * {@code Max_Time}, and {@code Std_Dev} (Requirement 5.2).
     *
     * @param point           the candidate meeting-point coordinate
     * @param perParticipant  travel time from each participant to {@code point};
     *                        must be non-null and contain at least one entry
     * @return the evaluated candidate carrying the point, its travel times, and
     *         the three metrics
     * @throws IllegalArgumentException if {@code point} or {@code perParticipant}
     *         is null, the map is empty, or it contains null keys/values
     */
    public EvaluatedCandidate evaluate(Coordinate point, Map<ParticipantId, Minutes> perParticipant) {
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

        double sumTime = sumTime(perParticipant);
        int maxTime = maxTime(perParticipant);
        double stdDev = populationStdDev(perParticipant);

        return new EvaluatedCandidate(point, perParticipant, sumTime, maxTime, stdDev);
    }

    /**
     * {@code Sum_Time = Σ tᵢ}: the arithmetic sum of the per-participant travel
     * times, in minutes.
     *
     * @param perParticipant non-empty travel-time map
     * @return the sum of all travel times
     */
    public double sumTime(Map<ParticipantId, Minutes> perParticipant) {
        double sum = 0.0;
        for (Minutes minutes : perParticipant.values()) {
            sum += minutes.value();
        }
        return sum;
    }

    /**
     * {@code Max_Time = max tᵢ}: the largest single travel time, in whole minutes.
     *
     * @param perParticipant non-empty travel-time map
     * @return the maximum travel time
     */
    public int maxTime(Map<ParticipantId, Minutes> perParticipant) {
        int max = Integer.MIN_VALUE;
        for (Minutes minutes : perParticipant.values()) {
            if (minutes.value() > max) {
                max = minutes.value();
            }
        }
        return max;
    }

    /**
     * {@code Std_Dev = σ}: the <b>population</b> standard deviation of the
     * per-participant travel times, {@code sqrt( (1/n) · Σ (tᵢ − μ)² )} with
     * {@code μ = (Σ tᵢ) / n}. Dividing by {@code n} (not {@code n − 1}) is
     * deliberate: the group's travel-time vector is the whole population, and this
     * keeps {@code σ = 0} well-defined for a single participant.
     *
     * @param perParticipant non-empty travel-time map
     * @return the population standard deviation of the travel times
     */
    public double populationStdDev(Map<ParticipantId, Minutes> perParticipant) {
        int n = perParticipant.size();
        double mean = sumTime(perParticipant) / n;
        double sumSquaredDeviations = 0.0;
        for (Minutes minutes : perParticipant.values()) {
            double deviation = minutes.value() - mean;
            sumSquaredDeviations += deviation * deviation;
        }
        return Math.sqrt(sumSquaredDeviations / n);
    }

    /**
     * {@code μ = (Σ tᵢ) / n}: the arithmetic mean travel time, in minutes. Exposed
     * because strategy comparison and reporting (e.g. group-average travel time)
     * reuse it.
     *
     * @param perParticipant non-empty travel-time map
     * @return the mean travel time
     */
    public double meanTime(Map<ParticipantId, Minutes> perParticipant) {
        return sumTime(perParticipant) / perParticipant.size();
    }
}
