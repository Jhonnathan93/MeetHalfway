package app.meethalfway.meetings.application;

import app.meethalfway.meetings.domain.engine.RecommendationEngine;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.Meeting;
import app.meethalfway.meetings.domain.model.RecommendationOutcome;
import app.meethalfway.meetings.domain.model.MeetingRepository;
import app.meethalfway.routing.domain.port.RoutingProvider;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Use case: compute and store the recommendation for a meeting (Requirements
 * 2–9). It loads the meeting by {@code urlCode}, runs the domain
 * {@link RecommendationEngine} over its input using the injected
 * {@link RoutingProvider} and {@link EngineConfig}, and:
 * <ul>
 *   <li>on {@link RecommendationOutcome.Success} — persists the outcome onto the
 *       meeting (so it is retained, Requirement 9.3) and returns it;</li>
 *   <li>on {@link RecommendationOutcome.RoutingFailure} — returns the failure
 *       <em>without</em> persisting any recommendation, so a participant is never
 *       silently dropped and no partial result is stored (Requirement 6).</li>
 * </ul>
 *
 * <p>Framework-free application code with explicit constructor injection; wired
 * as a bean at the composition root. It depends only on domain ports and types.
 */
public final class ComputeRecommendations {

    private final RecommendationEngine engine;
    private final MeetingRepository repository;
    private final RoutingProvider routing;
    private final EngineConfig config;

    /**
     * @param engine     the domain recommendation engine; must not be {@code null}
     * @param repository the meeting repository port; must not be {@code null}
     * @param routing    the routing provider port; must not be {@code null}
     * @param config     the engine configuration snapshot; must not be {@code null}
     */
    public ComputeRecommendations(
            RecommendationEngine engine,
            MeetingRepository repository,
            RoutingProvider routing,
            EngineConfig config) {
        if (engine == null) {
            throw new IllegalArgumentException("engine must not be null");
        }
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        if (routing == null) {
            throw new IllegalArgumentException("routing must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.engine = engine;
        this.repository = repository;
        this.routing = routing;
        this.config = config;
    }

    /**
     * Computes the recommendation for the meeting with the given URL code.
     *
     * @param urlCode the access code of the meeting to compute
     * @return the computed outcome (a {@code Success} is persisted; a
     *         {@code RoutingFailure} is not)
     * @throws NoSuchElementException if no meeting has the given URL code
     */
    public RecommendationOutcome compute(String urlCode) {
        Optional<Meeting> found = repository.findByUrlCode(urlCode);
        if (found.isEmpty()) {
            throw new NoSuchElementException("no meeting found for url code: " + urlCode);
        }
        Meeting meeting = found.get();

        RecommendationOutcome outcome = engine.compute(meeting.input(), config, routing);
        if (outcome instanceof RecommendationOutcome.Success) {
            Meeting withRecommendation =
                    new Meeting(meeting.urlCode(), meeting.input(), Optional.of(outcome));
            repository.save(withRecommendation);
        }
        return outcome;
    }
}
