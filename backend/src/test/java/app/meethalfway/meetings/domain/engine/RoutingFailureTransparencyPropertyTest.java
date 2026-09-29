package app.meethalfway.meetings.domain.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.MeetingInput;
import app.meethalfway.meetings.domain.model.OutlierRule;
import app.meethalfway.shared.domain.ParticipantId;
import app.meethalfway.meetings.domain.model.ParticipantInput;
import app.meethalfway.meetings.domain.model.RecommendationOutcome;
import app.meethalfway.meetings.domain.model.RoutingError;
import app.meethalfway.meetings.domain.model.ServiceBounds;
import app.meethalfway.shared.domain.TransportMode;
import app.meethalfway.shared.testing.FakeRoutingProvider;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for routing-failure transparency in
 * {@link RecommendationEngine}.
 *
 * <p>Feature: meeting-recommendation-engine, Property 13: Routing-failure transparency
 *
 * <p>Validates: Requirements 6.1, 6.3, 6.4 &mdash; when the routing provider
 * fails for at least one participant location, the engine returns a
 * routing-failure outcome that identifies <em>each</em> affected participant
 * location and its specific reason, and <em>never</em> returns strategy
 * recommendations. No participant is silently excluded.
 *
 * <p>Each iteration builds a valid meeting (2&ndash;10 participants, all inside a
 * fixed {@link ServiceBounds}, whole distinct coordinates so the origin-keyed
 * fake never collides), picks a non-empty subset of participants to be
 * unroutable, and configures a {@link FakeRoutingProvider} that fails every
 * route from each chosen origin with a reason embedding that participant's id.
 * The remaining participants route fine via the fake's distance model. The test
 * then asserts the outcome is a {@link RecommendationOutcome.RoutingFailure}
 * whose errors name exactly the chosen subset &mdash; none missing, none extra,
 * each once &mdash; carrying the correct location and injected reason, and never
 * a {@link RecommendationOutcome.Success}.
 */
class RoutingFailureTransparencyPropertyTest {

    /**
     * Fixed served region for the generated meetings. Coordinates are generated
     * strictly inside this box so {@link MeetingValidator} never rejects the
     * meeting before routing runs.
     */
    private static final ServiceBounds SERVICE_BOUNDS =
            new ServiceBounds(6.10, 6.40, -75.70, -75.40);

    private static final double MIN_LAT = 6.11;
    private static final double MAX_LAT = 6.39;
    private static final double MIN_LNG = -75.69;
    private static final double MAX_LNG = -75.41;

    /** Distinct-coordinate lattice: enough cells for the max 10 participants. */
    private static final int GRID_STEPS = 12;

    private final RecommendationEngine engine = new RecommendationEngine(
            new MeetingValidator(),
            new GridCandidateGenerator(),
            new MetricCalculator(),
            new OutlierDetector());

    private static EngineConfig config() {
        return new EngineConfig(
                SERVICE_BOUNDS,
                25,
                15_000.0,
                new OutlierRule(2.0),
                0.5);
    }

    /**
     * A generated meeting together with the subset of participant indices whose
     * locations are unroutable.
     *
     * @param participants the meeting participants (2&ndash;10, distinct in-bounds
     *                      locations)
     * @param failing      the non-empty set of indices (into {@code participants})
     *                      whose origins the fake will fail
     */
    private record Scenario(List<ParticipantInput> participants, Set<Integer> failing) {
    }

    /**
     * Generates a valid meeting plus a non-empty failing subset. Participants get
     * distinct coordinates drawn from a lattice inside the service bounds so the
     * origin-keyed fake maps each participant to exactly one origin (no
     * collisions between failing and routable origins). The failing subset is a
     * non-empty selection of the participant indices.
     */
    @Provide
    Arbitrary<Scenario> scenarios() {
        Arbitrary<Integer> participantCount = Arbitraries.integers().between(2, 10);

        return participantCount.flatMap(count -> {
            // Distinct lattice cells → distinct coordinates, one per participant.
            Arbitrary<Set<Integer>> cells = Arbitraries.integers()
                    .between(0, GRID_STEPS * GRID_STEPS - 1)
                    .set()
                    .ofSize(count);

            return cells.flatMap(cellSet -> {
                List<ParticipantInput> participants = buildParticipants(cellSet);

                // Non-empty subset of indices [0, count) to mark unroutable.
                Arbitrary<Set<Integer>> failing = Arbitraries.integers()
                        .between(0, count - 1)
                        .set()
                        .ofMinSize(1)
                        .ofMaxSize(count);

                return failing.map(f -> new Scenario(participants, f));
            });
        });
    }

    private static List<ParticipantInput> buildParticipants(Set<Integer> cells) {
        List<ParticipantInput> participants = new ArrayList<>();
        int index = 0;
        for (int cell : cells) {
            int latStep = cell / GRID_STEPS;
            int lngStep = cell % GRID_STEPS;
            double lat = MIN_LAT + (MAX_LAT - MIN_LAT) * latStep / (GRID_STEPS - 1);
            double lng = MIN_LNG + (MAX_LNG - MIN_LNG) * lngStep / (GRID_STEPS - 1);
            ParticipantId id = new ParticipantId("p" + index);
            participants.add(new ParticipantInput(id, "p" + index, new Coordinate(lat, lng)));
            index++;
        }
        return participants;
    }

    /**
     * Property 13 &mdash; a meeting with one or more unroutable locations yields a
     * routing-failure outcome that names exactly the affected participants (each
     * once) with their location and specific reason, and never a success with
     * recommendations.
     */
    @Property(tries = 100)
    void routingFailureNamesEachAffectedLocationAndNeverRecommends(
            @ForAll("scenarios") Scenario scenario) {

        List<ParticipantInput> participants = scenario.participants();
        Set<Integer> failingIndices = scenario.failing();

        FakeRoutingProvider.Builder routingBuilder = FakeRoutingProvider.builder();
        Set<ParticipantId> expectedFailingIds = new HashSet<>();
        for (int failingIndex : failingIndices) {
            ParticipantInput failingParticipant = participants.get(failingIndex);
            String reason = "unroutable: " + failingParticipant.id().value();
            routingBuilder.withFailingOrigin(failingParticipant.location(), reason);
            expectedFailingIds.add(failingParticipant.id());
        }
        FakeRoutingProvider routing = routingBuilder.build();

        MeetingInput input = new MeetingInput(
                new ArrayList<>(participants), TransportMode.DRIVING);

        RecommendationOutcome outcome = engine.compute(input, config(), routing);

        // Never a Success: no participant is silently excluded (Requirements 6.3, 6.4).
        assertThat(outcome)
                .as("routing failure must never produce strategy recommendations")
                .isInstanceOf(RecommendationOutcome.RoutingFailure.class);

        RecommendationOutcome.RoutingFailure failure =
                (RecommendationOutcome.RoutingFailure) outcome;

        // Each affected participant is identified exactly once (Requirement 6.1).
        Set<ParticipantId> reportedIds = new HashSet<>();
        for (RoutingError error : failure.errors()) {
            reportedIds.add(error.participantId());
        }
        assertThat(reportedIds)
                .as("every unroutable participant is reported, none missing, none extra")
                .isEqualTo(expectedFailingIds);
        assertThat(failure.errors())
                .as("each affected participant is reported exactly once")
                .hasSize(expectedFailingIds.size());

        // Each error carries the correct location and the injected, non-blank reason.
        for (RoutingError error : failure.errors()) {
            int reportedIndex = indexOf(participants, error.participantId());
            assertThat(reportedIndex)
                    .as("reported participant belongs to the meeting")
                    .isGreaterThanOrEqualTo(0);
            ParticipantInput reported = participants.get(reportedIndex);
            assertThat(error.location())
                    .as("routing error echoes the participant's own location")
                    .isEqualTo(reported.location());
            assertThat(error.reason())
                    .as("routing error carries the specific injected reason")
                    .isEqualTo("unroutable: " + error.participantId().value())
                    .isNotBlank();
        }
    }

    private static int indexOf(List<ParticipantInput> participants, ParticipantId id) {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
