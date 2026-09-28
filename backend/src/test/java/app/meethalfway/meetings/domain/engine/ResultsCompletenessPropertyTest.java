package app.meethalfway.meetings.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.assertj.core.data.Offset;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.MeetingInput;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.meetings.domain.model.OutlierRule;
import app.meethalfway.shared.domain.ParticipantId;
import app.meethalfway.meetings.domain.model.ParticipantInput;
import app.meethalfway.meetings.domain.model.RecommendationOutcome;
import app.meethalfway.meetings.domain.model.ServiceBounds;
import app.meethalfway.meetings.domain.model.StrategyResult;
import app.meethalfway.meetings.domain.model.StrategyResults;
import app.meethalfway.shared.domain.TransportMode;
import app.meethalfway.shared.testing.FakeRoutingProvider;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Feature: meeting-recommendation-engine, Property 12: Results completeness and shape
 *
 * <p>For any successful computation, the output contains exactly one result for
 * each of the Fastest, Minimax, and Fairest strategies; each result carries a
 * valid in-range coordinate and, for every participant in the meeting, a
 * per-participant {@code Travel_Time} alongside consistent {@code Sum_Time},
 * {@code Max_Time}, and {@code Std_Dev} (Requirements 6.4, 8.1, 8.2, 8.3).
 *
 * <p>Validates: Requirements 6.4, 8.1, 8.2, 8.3.
 *
 * <p>Meetings are generated valid and fully routable: 2&ndash;10 participants
 * placed strictly inside a fixed {@link ServiceBounds} via fraction
 * interpolation, a valid {@link EngineConfig}, and
 * {@link FakeRoutingProvider#withDefaults()} so no route ever fails. The
 * computation must therefore be a {@link RecommendationOutcome.Success}; the
 * property then asserts the completeness and shape of all three strategy
 * results. Expected {@code Σ/max/σ} are recomputed independently from each
 * result's own per-participant vector (population std-dev, matching
 * {@link MetricCalculator}), so the assertion is not a re-run of the engine's
 * own metric computation.
 */
class ResultsCompletenessPropertyTest {

    private static final double TOLERANCE = 1e-9;

    /** Fixed served region; participants are generated strictly inside it. */
    private static final ServiceBounds BOUNDS = new ServiceBounds(6.10, 6.40, -75.70, -75.40);

    private final DefaultRecommendationEngine engine = new DefaultRecommendationEngine(
            new MeetingValidator(),
            new GridCandidateGenerator(),
            new MetricCalculator(),
            new ConfiguredOutlierDetector());

    /**
     * Property 12 &mdash; a valid, fully-routable meeting yields a Success whose
     * three strategy results are each complete (one per-participant time for
     * every participant, none omitted), carry an in-range coordinate with whole
     * non-negative times, and expose {@code Σ/max/σ} consistent with their own
     * per-participant vectors.
     */
    @Property(tries = 100)
    void successHasThreeCompleteWellShapedStrategyResults(
            @ForAll("meetings") MeetingInput meeting,
            @ForAll("configs") EngineConfig config) {

        RecommendationOutcome outcome =
                engine.compute(meeting, config, FakeRoutingProvider.withDefaults());

        // Fully routable by construction, so the outcome must be a Success.
        assertThat(outcome).isInstanceOf(RecommendationOutcome.Success.class);
        RecommendationOutcome.Success success = (RecommendationOutcome.Success) outcome;

        StrategyResults results = success.results();
        assertThat(results.fastest()).isNotNull();
        assertThat(results.minimax()).isNotNull();
        assertThat(results.fairest()).isNotNull();

        Set<ParticipantId> expectedIds = new java.util.HashSet<>();
        for (ParticipantInput participant : meeting.participants()) {
            expectedIds.add(participant.id());
        }

        for (StrategyResult result : List.of(
                results.fastest(), results.minimax(), results.fairest())) {
            assertResultCompleteAndConsistent(result, expectedIds);
        }
    }

    private static void assertResultCompleteAndConsistent(
            StrategyResult result, Set<ParticipantId> expectedIds) {

        // In-range coordinate (Requirement 8.1).
        assertThat(result.point().lat()).isBetween(-90.0, 90.0);
        assertThat(result.point().lng()).isBetween(-180.0, 180.0);

        // Completeness: a per-participant time for EVERY participant, none omitted
        // and none extra (Requirements 6.4, 8.2).
        Map<ParticipantId, Minutes> perParticipant = result.perParticipant();
        assertThat(perParticipant.keySet()).isEqualTo(expectedIds);

        // Every travel time is a whole, non-negative number of minutes (Requirement 8.2).
        List<Integer> times = new ArrayList<>();
        for (Minutes minutes : perParticipant.values()) {
            assertThat(minutes.value()).isGreaterThanOrEqualTo(0);
            times.add(minutes.value());
        }

        // Metric consistency vs the result's own per-participant vector
        // (Requirements 8.3): Σ = sum, max = maximum, σ = population std-dev.
        int n = times.size();
        double expectedSum = 0.0;
        int expectedMax = Integer.MIN_VALUE;
        for (int t : times) {
            expectedSum += t;
            if (t > expectedMax) {
                expectedMax = t;
            }
        }
        double mean = expectedSum / n;
        double sumSquaredDeviations = 0.0;
        for (int t : times) {
            double deviation = t - mean;
            sumSquaredDeviations += deviation * deviation;
        }
        double expectedStdDev = Math.sqrt(sumSquaredDeviations / n);

        assertThat(result.sumTime()).isCloseTo(expectedSum, Offset.offset(TOLERANCE));
        assertThat(result.maxTime()).isEqualTo(expectedMax);
        assertThat(result.stdDev()).isCloseTo(expectedStdDev, Offset.offset(TOLERANCE));
    }

    // ---- Generators ----

    /**
     * Valid, fully-routable meetings: 2&ndash;10 participants with unique ids,
     * each located strictly inside {@link #BOUNDS} via fraction interpolation,
     * and a randomized supported transport mode. Participants use a mutable
     * {@link ArrayList} as {@link MeetingInput} requires.
     */
    @Provide
    Arbitrary<MeetingInput> meetings() {
        Arbitrary<Integer> counts = Arbitraries.integers().between(2, 10);
        Arbitrary<TransportMode> modes = Arbitraries.of(TransportMode.class);
        return counts.flatMap(count -> {
            Arbitrary<List<Coordinate>> locations = coordinateInBounds().list().ofSize(count);
            return Combinators.combine(locations, modes).as((points, mode) -> {
                List<ParticipantInput> participants = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    participants.add(new ParticipantInput(
                            new ParticipantId("p" + i), "Participant " + i, points.get(i)));
                }
                return new MeetingInput(participants, mode);
            });
        });
    }

    /**
     * A coordinate strictly inside {@link #BOUNDS}, built by interpolating each
     * axis with a fraction in {@code (0, 1)} so it always satisfies the service
     * bounds and never lands on the edge.
     */
    private Arbitrary<Coordinate> coordinateInBounds() {
        Arbitrary<Double> latFraction = Arbitraries.doubles().between(0.05, 0.95).ofScale(6);
        Arbitrary<Double> lngFraction = Arbitraries.doubles().between(0.05, 0.95).ofScale(6);
        return Combinators.combine(latFraction, lngFraction).as((latF, lngF) -> {
            double lat = BOUNDS.minLat() + latF * (BOUNDS.maxLat() - BOUNDS.minLat());
            double lng = BOUNDS.minLng() + lngF * (BOUNDS.maxLng() - BOUNDS.minLng());
            return new Coordinate(lat, lng);
        });
    }

    /**
     * Valid engine configurations over the fixed bounds: varying grid density,
     * search radius, outlier rule, and ε, all within sane ranges.
     */
    @Provide
    Arbitrary<EngineConfig> configs() {
        Arbitrary<Integer> gridDensity = Arbitraries.integers().between(4, 36);
        Arbitrary<Double> radius = Arbitraries.doubles().between(5_000.0, 25_000.0).ofScale(1);
        Arbitrary<Double> epsilon = Arbitraries.doubles().between(0.1, 1.0).ofScale(3);
        Arbitrary<OutlierRule> rules = Arbitraries.oneOf(
                Arbitraries.doubles().between(1.5, 4.0).ofScale(2)
                        .map(OutlierRule.MedianMultiple::new),
                Arbitraries.just(new OutlierRule.Percentile(90)));
        return Combinators.combine(gridDensity, radius, rules, epsilon)
                .as((n, r, rule, eps) -> new EngineConfig(BOUNDS, n, r, rule, eps));
    }
}
