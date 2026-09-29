package app.meethalfway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.meethalfway.meetings.domain.engine.RecommendationEngine;
import app.meethalfway.meetings.domain.engine.GridCandidateGenerator;
import app.meethalfway.meetings.domain.engine.MeetingValidator;
import app.meethalfway.meetings.domain.engine.MetricCalculator;
import app.meethalfway.meetings.domain.engine.OutlierDetector;
import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.Meeting;
import app.meethalfway.meetings.domain.model.MeetingInput;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.meetings.domain.model.OutlierRule;
import app.meethalfway.shared.domain.ParticipantId;
import app.meethalfway.meetings.domain.model.ParticipantInput;
import app.meethalfway.meetings.domain.model.RecommendationOutcome;
import app.meethalfway.meetings.domain.model.ServiceBounds;
import app.meethalfway.shared.domain.TransportMode;
import app.meethalfway.routing.domain.port.RoutingProvider;
import app.meethalfway.routing.domain.port.RouteResult;
import app.meethalfway.meetings.application.RecommendationService;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RecommendationService} (Task 11.2, Requirements 6, 9.3).
 *
 * <p>Uses the real {@link RecommendationEngine} with deterministic routing and
 * an in-memory repository to verify persistence:
 * <ul>
 *   <li>a {@code Success} is stored onto the meeting;</li>
 *   <li>a {@code RoutingFailure} is returned but never persisted.</li>
 * </ul>
 */
class RecommendationServiceTest {

    private final InMemoryMeetingRepository repository = new InMemoryMeetingRepository();
    private final EngineConfig config = new EngineConfig(
            new ServiceBounds(-90.0, 90.0, -180.0, 180.0),
            9,
            20000.0,
            new OutlierRule(2.0),
            0.5);
    private final RecommendationEngine engine = new RecommendationEngine(
            new MeetingValidator(), new GridCandidateGenerator(), new MetricCalculator(), new OutlierDetector());

    @Test
    void successIsPersistedOntoTheMeeting() {
        Meeting meeting = seededMeeting("COMP0001");
        RoutingProvider routing = (origin, destination, mode) ->
                RouteResult.success(new Minutes(origin.lat() > 6.245 ? 12 : 10));
        RecommendationService useCase = new RecommendationService(
                engine, repository, routing, config);

        RecommendationOutcome outcome = useCase.compute("COMP0001");

        assertThat(outcome).isInstanceOf(RecommendationOutcome.Success.class);
        Meeting stored = repository.findByUrlCode("COMP0001").orElseThrow();
        assertThat(stored.recommendation()).contains((RecommendationOutcome.Success) outcome);
    }

    @Test
    void routingFailureIsReturnedButNotPersisted() {
        seededMeeting("COMP0002");
        RoutingProvider routing = (origin, destination, mode) -> RouteResult.failure("location is unreachable");
        RecommendationService useCase = new RecommendationService(
                engine, repository, routing, config);

        RecommendationOutcome outcome = useCase.compute("COMP0002");

        assertThat(outcome).isInstanceOf(RecommendationOutcome.RoutingFailure.class);
        assertThat(((RecommendationOutcome.RoutingFailure) outcome).errors()).hasSize(2);
        Meeting stored = repository.findByUrlCode("COMP0002").orElseThrow();
        assertThat(stored.recommendation()).isEmpty();
    }

    @Test
    void computingAnUnknownMeetingThrows() {
        RecommendationService useCase = new RecommendationService(
                engine, repository, (origin, destination, mode) -> RouteResult.failure("unused"), config);

        assertThatThrownBy(() -> useCase.compute("MISSING0"))
                .isInstanceOf(NoSuchElementException.class);
    }

    private Meeting seededMeeting(String urlCode) {
        Meeting meeting = Meeting.of(urlCode, new MeetingInput(
                new java.util.ArrayList<>(List.of(
                        new ParticipantInput(
                                new ParticipantId(UUID.randomUUID().toString()),
                                "Ana",
                                new Coordinate(6.24, -75.58)),
                        new ParticipantInput(
                                new ParticipantId(UUID.randomUUID().toString()),
                                "Bruno",
                                new Coordinate(6.25, -75.56)))),
                TransportMode.DRIVING));
        repository.seed(meeting);
        return meeting;
    }

}
