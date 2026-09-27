package app.meethalfway.domain.engine;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.EngineConfig;
import app.meethalfway.domain.model.MeetingInput;
import app.meethalfway.domain.model.OutlierRule;
import app.meethalfway.domain.model.OutlierTradeoff;
import app.meethalfway.domain.model.ParticipantId;
import app.meethalfway.domain.model.ParticipantInput;
import app.meethalfway.domain.model.RecommendationOutcome;
import app.meethalfway.domain.model.RoutingError;
import app.meethalfway.domain.model.ServiceBounds;
import app.meethalfway.domain.model.StrategyResult;
import app.meethalfway.domain.model.StrategyResults;
import app.meethalfway.domain.model.TransportMode;
import app.meethalfway.domain.testing.FakeRoutingProvider;

/**
 * Example-based unit tests for {@link DefaultRecommendationEngine} wiring
 * (task 7.1). These pin down the two headline behaviors: a fully routable
 * meeting yields a {@link RecommendationOutcome.Success} with one result per
 * strategy over every participant (Requirements 8.1, 8.2, 8.3), and a meeting
 * with an unroutable location yields a {@link RecommendationOutcome.RoutingFailure}
 * naming the affected participant and reason, never a {@code Success}
 * (Requirements 6.1, 6.3, 6.4).
 *
 * <p>The randomized universal properties (routing-failure transparency,
 * results completeness, determinism) are covered separately by the property
 * tests in tasks 7.3&ndash;7.5.
 */
class DefaultRecommendationEngineTest {

    private static final Coordinate ALICE = new Coordinate(6.20, -75.60);
    private static final Coordinate BOB = new Coordinate(6.25, -75.55);
    private static final Coordinate CAROL = new Coordinate(6.30, -75.50);

    private final DefaultRecommendationEngine engine = new DefaultRecommendationEngine(
            new MeetingValidator(),
            new GridCandidateGenerator(),
            new MetricCalculator(),
            new ConfiguredOutlierDetector());

    private static EngineConfig config() {
        return new EngineConfig(
                new ServiceBounds(6.10, 6.40, -75.70, -75.40),
                25,
                15_000.0,
                new OutlierRule.MedianMultiple(2.0),
                0.5);
    }

    private static ParticipantInput participant(String id, Coordinate location) {
        return new ParticipantInput(new ParticipantId(id), id, location);
    }

    private static MeetingInput meeting(ParticipantInput... participants) {
        return new MeetingInput(new java.util.ArrayList<>(List.of(participants)), TransportMode.DRIVING);
    }

    @Test
    void happyPathReturnsSuccessWithThreeStrategyResultsForEveryParticipant() {
        MeetingInput input = meeting(
                participant("alice", ALICE),
                participant("bob", BOB),
                participant("carol", CAROL));

        RecommendationOutcome outcome =
                engine.compute(input, config(), FakeRoutingProvider.withDefaults());

        assertThat(outcome).isInstanceOf(RecommendationOutcome.Success.class);
        RecommendationOutcome.Success success = (RecommendationOutcome.Success) outcome;

        // Outlier trade-off is empty at task 7.1 (populated by 7.2).
        assertThat(success.tradeoff()).isEmpty();

        StrategyResults results = success.results();
        assertThat(results.fastest()).isNotNull();
        assertThat(results.minimax()).isNotNull();
        assertThat(results.fairest()).isNotNull();

        // Each strategy result carries a per-participant time for all three
        // participants and an in-range coordinate (Requirements 8.1, 8.2, 8.3).
        for (StrategyResult result : List.of(results.fastest(), results.minimax(), results.fairest())) {
            assertThat(result.perParticipant())
                    .containsOnlyKeys(
                            new ParticipantId("alice"),
                            new ParticipantId("bob"),
                            new ParticipantId("carol"));
            assertThat(result.point().lat()).isBetween(-90.0, 90.0);
            assertThat(result.point().lng()).isBetween(-180.0, 180.0);
            assertThat(result.sumTime()).isGreaterThanOrEqualTo(0.0);
            assertThat(result.maxTime()).isGreaterThanOrEqualTo(0);
            assertThat(result.stdDev()).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Test
    void unroutableParticipantReturnsRoutingFailureNamingThatParticipant() {
        MeetingInput input = meeting(
                participant("alice", ALICE),
                participant("bob", BOB),
                participant("carol", CAROL));

        // Bob's location cannot be routed for any candidate.
        FakeRoutingProvider routing = FakeRoutingProvider.builder()
                .withFailingOrigin(BOB, "destination unreachable")
                .build();

        RecommendationOutcome outcome = engine.compute(input, config(), routing);

        assertThat(outcome).isInstanceOf(RecommendationOutcome.RoutingFailure.class);
        RecommendationOutcome.RoutingFailure failure = (RecommendationOutcome.RoutingFailure) outcome;

        // Exactly the affected participant is reported, with its location and reason.
        assertThat(failure.errors()).hasSize(1);
        RoutingError error = failure.errors().get(0);
        assertThat(error.participantId()).isEqualTo(new ParticipantId("bob"));
        assertThat(error.location()).isEqualTo(BOB);
        assertThat(error.reason()).isEqualTo("destination unreachable");
    }

    @Test
    void reportsEachAffectedParticipantExactlyOnce() {
        MeetingInput input = meeting(
                participant("alice", ALICE),
                participant("bob", BOB),
                participant("carol", CAROL));

        FakeRoutingProvider routing = FakeRoutingProvider.builder()
                .withFailingOrigin(ALICE, "timeout")
                .withFailingOrigin(CAROL, "out of coverage")
                .build();

        RecommendationOutcome outcome = engine.compute(input, config(), routing);

        assertThat(outcome).isInstanceOf(RecommendationOutcome.RoutingFailure.class);
        RecommendationOutcome.RoutingFailure failure = (RecommendationOutcome.RoutingFailure) outcome;

        assertThat(failure.errors()).hasSize(2);
        assertThat(failure.errors())
                .extracting(RoutingError::participantId)
                .containsExactlyInAnyOrder(new ParticipantId("alice"), new ParticipantId("carol"));
    }

    @Test
    void noOutlierYieldsEmptyTradeoff() {
        MeetingInput input = meeting(
                participant("alice", ALICE),
                participant("bob", BOB),
                participant("carol", CAROL));

        // Default fake: travel time scales with distance, no exaggerated participant.
        RecommendationOutcome outcome =
                engine.compute(input, config(), FakeRoutingProvider.withDefaults());

        assertThat(outcome).isInstanceOf(RecommendationOutcome.Success.class);
        RecommendationOutcome.Success success = (RecommendationOutcome.Success) outcome;
        assertThat(success.tradeoff()).isEmpty();
    }

    @Test
    void outlierYieldsTradeoffWithBothVariantsAndFiniteAverages() {
        MeetingInput input = meeting(
                participant("alice", ALICE),
                participant("bob", BOB),
                participant("carol", CAROL));

        // Carol is exaggerated far for every candidate, so her travel times dwarf the
        // rest; under the k=2 median-multiple rule she is flagged as an outlier.
        FakeRoutingProvider routing = FakeRoutingProvider.builder()
                .withOutlierOrigin(CAROL, 20.0)
                .build();

        RecommendationOutcome outcome = engine.compute(input, config(), routing);

        assertThat(outcome).isInstanceOf(RecommendationOutcome.Success.class);
        RecommendationOutcome.Success success = (RecommendationOutcome.Success) outcome;

        // A trade-off is present and flags exactly Carol.
        assertThat(success.tradeoff()).isPresent();
        OutlierTradeoff tradeoff = success.tradeoff().get();
        assertThat(tradeoff.outliers()).containsExactly(new ParticipantId("carol"));

        // The including field MUST equal the top-level (primary) results — the outlier
        // is never dropped from the primary recommendation (Requirement 7.5).
        assertThat(tradeoff.including()).isSameAs(success.results());

        // The including variant keeps all three participants; the excluding variant
        // drops the outlier from every strategy result (Requirement 7.2).
        for (StrategyResult result : List.of(
                tradeoff.including().fastest(),
                tradeoff.including().minimax(),
                tradeoff.including().fairest())) {
            assertThat(result.perParticipant()).containsOnlyKeys(
                    new ParticipantId("alice"),
                    new ParticipantId("bob"),
                    new ParticipantId("carol"));
        }
        for (StrategyResult result : List.of(
                tradeoff.excluding().fastest(),
                tradeoff.excluding().minimax(),
                tradeoff.excluding().fairest())) {
            assertThat(result.perParticipant()).containsOnlyKeys(
                    new ParticipantId("alice"),
                    new ParticipantId("bob"));
        }

        // Both group-average travel times are finite and non-negative (Requirement 7.3).
        assertThat(tradeoff.avgTravelTimeIncluding())
                .isFinite()
                .isGreaterThanOrEqualTo(0.0);
        assertThat(tradeoff.avgTravelTimeExcluding())
                .isFinite()
                .isGreaterThanOrEqualTo(0.0);

        // Dropping the exaggerated participant lowers the group average travel time.
        assertThat(tradeoff.avgTravelTimeExcluding())
                .isLessThan(tradeoff.avgTravelTimeIncluding());
    }

    @Test
    void rejectsNullArguments() {
        MeetingInput input = meeting(participant("alice", ALICE), participant("bob", BOB));
        assertThatThrownBy(() -> engine.compute(null, config(), FakeRoutingProvider.withDefaults()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.compute(input, null, FakeRoutingProvider.withDefaults()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.compute(input, config(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullDependencies() {
        assertThatThrownBy(() -> new DefaultRecommendationEngine(
                null, new GridCandidateGenerator(), new MetricCalculator(), new ConfiguredOutlierDetector()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DefaultRecommendationEngine(
                new MeetingValidator(), null, new MetricCalculator(), new ConfiguredOutlierDetector()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DefaultRecommendationEngine(
                new MeetingValidator(), new GridCandidateGenerator(), null, new ConfiguredOutlierDetector()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DefaultRecommendationEngine(
                new MeetingValidator(), new GridCandidateGenerator(), new MetricCalculator(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
