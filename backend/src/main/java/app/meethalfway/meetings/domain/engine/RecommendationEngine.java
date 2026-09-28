package app.meethalfway.meetings.domain.engine;

import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.MeetingInput;
import app.meethalfway.meetings.domain.model.RecommendationOutcome;
import app.meethalfway.routing.domain.port.RoutingProvider;

/**
 * The orchestrator of a single recommendation computation (Requirements 2&ndash;8).
 *
 * <p>The engine owns no I/O of its own: it receives a {@link RoutingProvider} and
 * an {@link EngineConfig} snapshot by injection and drives the pure engine
 * components (candidate generation, metric computation, the three strategy
 * selectors, and outlier detection). It never throws for an <em>expected</em>
 * routing failure; instead it returns a {@link RecommendationOutcome} whose
 * variant tells the caller whether recommendations were produced or a location
 * could not be routed:
 *
 * <ul>
 *   <li>{@link RecommendationOutcome.Success} — one result per strategy, plus an
 *       optional outlier trade-off (Requirement 8; the trade-off is populated by
 *       task 7.2).</li>
 *   <li>{@link RecommendationOutcome.RoutingFailure} — one
 *       {@link app.meethalfway.meetings.domain.model.RoutingError RoutingError} per
 *       affected participant location, so a participant is never silently omitted
 *       (Requirements 6.1, 6.3, 6.4).</li>
 * </ul>
 *
 * <p>Universal validation ({@link MeetingInput} participant count and coordinate
 * ranges) is enforced by the value objects on construction; service-bounds
 * validation is applied here through {@link MeetingValidator} against the
 * supplied config (Requirement 1.7). This is framework-free domain logic with no
 * dependency on HTTP, persistence, or any concrete provider.
 */
public interface RecommendationEngine {

    /**
     * Computes the Fastest, Minimax, and Fairest meeting points for the given
     * meeting using the supplied routing provider and configuration.
     *
     * @param input   the validated meeting input (2&ndash;10 participants, shared
     *                transport mode); must not be null
     * @param config  the externalized engine configuration; must not be null
     * @param routing the routing provider used to measure real travel times; must
     *                not be null
     * @return a {@link RecommendationOutcome.Success} with the three strategy
     *         results, or a {@link RecommendationOutcome.RoutingFailure} listing
     *         every participant location that could not be routed
     * @throws IllegalArgumentException                       if any argument is null
     * @throws MeetingValidator.OutOfServiceBoundsException if a participant
     *                                                       location falls outside
     *                                                       the configured service
     *                                                       bounds (Requirement 1.7)
     */
    RecommendationOutcome compute(MeetingInput input, EngineConfig config, RoutingProvider routing);
}
