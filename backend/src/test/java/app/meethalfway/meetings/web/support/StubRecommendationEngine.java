package app.meethalfway.meetings.web.support;

import app.meethalfway.meetings.domain.engine.RecommendationEngine;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.MeetingInput;
import app.meethalfway.meetings.domain.model.RecommendationOutcome;
import app.meethalfway.routing.domain.port.RoutingProvider;

/**
 * Controllable fake {@link RecommendationEngine} for web-adapter tests: it
 * returns a preset {@link RecommendationOutcome}, letting a controller test drive
 * the success (200) and routing-failure (422) paths of
 * {@code ComputeRecommendations} without running the real optimization.
 *
 * <p>Used because the concrete use cases are {@code final} (not mockable under
 * JDK 25 / Byte Buddy), so they are constructed for real against this fake engine.
 */
public final class StubRecommendationEngine implements RecommendationEngine {

    private RecommendationOutcome nextOutcome;

    /**
     * Sets the outcome the next {@link #compute} call returns.
     *
     * @param outcome the outcome to return
     */
    public void willReturn(RecommendationOutcome outcome) {
        this.nextOutcome = outcome;
    }

    @Override
    public RecommendationOutcome compute(
            MeetingInput input, EngineConfig config, RoutingProvider routing) {
        if (nextOutcome == null) {
            throw new IllegalStateException("no outcome configured for the stub engine");
        }
        return nextOutcome;
    }
}
