package app.meethalfway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.meethalfway.domain.engine.RecommendationEngine;
import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.EngineConfig;
import app.meethalfway.domain.model.Meeting;
import app.meethalfway.domain.model.MeetingInput;
import app.meethalfway.domain.model.Minutes;
import app.meethalfway.domain.model.OutlierRule;
import app.meethalfway.domain.model.ParticipantId;
import app.meethalfway.domain.model.ParticipantInput;
import app.meethalfway.domain.model.RecommendationOutcome;
import app.meethalfway.domain.model.RoutingError;
import app.meethalfway.domain.model.ServiceBounds;
import app.meethalfway.domain.model.StrategyResult;
import app.meethalfway.domain.model.StrategyResults;
import app.meethalfway.domain.model.TransportMode;
import app.meethalfway.domain.port.RoutingProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ComputeRecommendations} (Task 11.2, Requirements 6, 9.3).
 *
 * <p>Uses a stub {@link RecommendationEngine} to drive both outcome branches
 * deterministically and the in-memory {@link app.meethalfway.domain.port.MeetingRepository}
 * fake to observe persistence:
 * <ul>
 *   <li>a {@code Success} is stored onto the meeting;</li>
 *   <li>a {@code RoutingFailure} is returned but never persisted.</li>
 * </ul>
 */
class ComputeRecommendationsTest {

    private final InMemoryMeetingRepository repository = new InMemoryMeetingRepository();
    private final EngineConfig config = new EngineConfig(
            new ServiceBounds(-90.0, 90.0, -180.0, 180.0),
            9,
            20000.0,
            new OutlierRule.MedianMultiple(2.0),
            0.5);
    // The routing provider is never called by the stub engine; a throwing stub
    // proves ComputeRecommendations delegates routing to the engine, not itself.
    private final RoutingProvider routing = (origin, destination, mode) -> {
        throw new AssertionError("routing must be driven by the engine, not the use case");
    };

    @Test
    void successIsPersistedOntoTheMeeting() {
        Meeting meeting = seededMeeting("COMP0001");
        RecommendationOutcome.Success success =
                RecommendationOutcome.Success.of(results(meeting));
        ComputeRecommendations useCase = new ComputeRecommendations(
                (input, cfg, routingProvider) -> success, repository, routing, config);

        RecommendationOutcome outcome = useCase.compute("COMP0001");

        assertThat(outcome).isSameAs(success);
        Meeting stored = repository.findByUrlCode("COMP0001").orElseThrow();
        assertThat(stored.recommendation()).contains(success);
    }

    @Test
    void routingFailureIsReturnedButNotPersisted() {
        seededMeeting("COMP0002");
        RecommendationOutcome.RoutingFailure failure = new RecommendationOutcome.RoutingFailure(
                new java.util.ArrayList<>(List.of(new RoutingError(
                        new ParticipantId(UUID.randomUUID().toString()),
                        new Coordinate(6.24, -75.58),
                        "location is unreachable"))));
        ComputeRecommendations useCase = new ComputeRecommendations(
                (input, cfg, routingProvider) -> failure, repository, routing, config);

        RecommendationOutcome outcome = useCase.compute("COMP0002");

        assertThat(outcome).isSameAs(failure);
        Meeting stored = repository.findByUrlCode("COMP0002").orElseThrow();
        assertThat(stored.recommendation()).isEmpty();
    }

    @Test
    void computingAnUnknownMeetingThrows() {
        ComputeRecommendations useCase = new ComputeRecommendations(
                (input, cfg, routingProvider) -> RecommendationOutcome.Success.of(null),
                repository, routing, config);

        assertThatThrownBy(() -> useCase.compute("MISSING0"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void constructorRejectsNullDependencies() {
        RecommendationEngine engine = (input, cfg, routingProvider) -> null;
        assertThatThrownBy(() -> new ComputeRecommendations(null, repository, routing, config))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComputeRecommendations(engine, null, routing, config))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComputeRecommendations(engine, repository, null, config))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComputeRecommendations(engine, repository, routing, null))
                .isInstanceOf(IllegalArgumentException.class);
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

    private StrategyResults results(Meeting meeting) {
        List<ParticipantInput> participants = meeting.input().participants();
        return new StrategyResults(
                strategyResult(participants, 5),
                strategyResult(participants, 6),
                strategyResult(participants, 7));
    }

    private StrategyResult strategyResult(List<ParticipantInput> participants, int base) {
        Map<ParticipantId, Minutes> perParticipant = new LinkedHashMap<>();
        int sum = 0;
        int max = 0;
        int offset = 0;
        for (ParticipantInput participant : participants) {
            int minutes = base + offset;
            perParticipant.put(participant.id(), new Minutes(minutes));
            sum += minutes;
            max = Math.max(max, minutes);
            offset++;
        }
        return new StrategyResult(new Coordinate(6.23, -75.57), perParticipant, sum, max, 1.0);
    }
}
