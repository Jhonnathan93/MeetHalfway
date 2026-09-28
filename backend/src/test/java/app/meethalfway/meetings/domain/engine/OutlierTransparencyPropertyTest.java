package app.meethalfway.meetings.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.MeetingInput;
import app.meethalfway.meetings.domain.model.OutlierRule;
import app.meethalfway.meetings.domain.model.OutlierTradeoff;
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
 * Feature: meeting-recommendation-engine, Property 15: Outlier transparency
 *
 * <p>When a meeting contains at least one outlier, the engine's output includes
 * <em>both</em> the including and the excluding computations for all three
 * strategies, each with a selected point and a group-average travel time, and it
 * never drops an outlier without presenting the comparison (Requirements 7.2,
 * 7.3, 7.5).
 *
 * <p>Validates: Requirements 7.2, 7.3, 7.5.
 *
 * <p><b>Faithfulness.</b> The premise "with &ge; 1 outlier" is defined by the
 * engine itself: outliers are the participants the configured
 * {@link ConfiguredOutlierDetector} flags on the Fastest (including) winner's
 * per-participant travel-time vector (task 7.2). Rather than assuming the
 * injected participant is always flagged, this test re-derives the expected
 * outlier set independently from the returned primary results and asserts the
 * transparency contract exactly when that set is non-empty (and asserts an empty
 * trade-off otherwise). Each scenario injects one exaggerated participant via
 * {@link FakeRoutingProvider.Builder#withOutlierOrigin}, so the outlier path is
 * exercised on the vast majority of iterations while the property stays true to
 * the engine's own definition of an outlier.
 */
class OutlierTransparencyPropertyTest {

    /** Fixed service bounds; all generated origins fall inside this box. */
    private static final ServiceBounds BOUNDS = new ServiceBounds(6.00, 6.50, -75.80, -75.30);

    private final ConfiguredOutlierDetector detector = new ConfiguredOutlierDetector();

    private final DefaultRecommendationEngine engine = new DefaultRecommendationEngine(
            new MeetingValidator(),
            new GridCandidateGenerator(),
            new MetricCalculator(),
            detector);

    /**
     * For any valid meeting (3&ndash;10 participants with distinct in-bounds
     * locations) in which one participant's travel times are exaggerated, the
     * engine returns a {@link RecommendationOutcome.Success}, and its outlier
     * trade-off is present exactly when the configured detector flags at least
     * one outlier on the primary Fastest winner. When present, the trade-off
     * satisfies the full transparency contract; when absent, no outlier was
     * detected.
     */
    @Property(tries = 100)
    void outlierTradeoffPresentsBothComputationsForAllThreeStrategies(
            @ForAll("scenarios") Scenario scenario) {

        RecommendationOutcome outcome =
                engine.compute(scenario.meeting(), scenario.config(), scenario.routing());

        assertThat(outcome).isInstanceOf(RecommendationOutcome.Success.class);
        RecommendationOutcome.Success success = (RecommendationOutcome.Success) outcome;
        StrategyResults results = success.results();

        // Re-derive the engine's own outlier premise: the configured rule applied
        // to the primary Fastest winner's per-participant travel-time vector.
        Set<ParticipantId> expectedOutliers =
                detector.detect(results.fastest().perParticipant(), scenario.config());

        Set<ParticipantId> allIds = scenario.meeting().participants().stream()
                .map(ParticipantInput::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Excluding the outlier(s) must leave at least two participants, otherwise the
        // engine's defensive guard returns an empty trade-off (never an invalid meeting).
        boolean nonOutlierRemains = allIds.size() - expectedOutliers.size() >= 1;

        if (expectedOutliers.isEmpty() || !nonOutlierRemains) {
            assertThat(success.tradeoff())
                    .as("no outlier detected (or none could be excluded) => empty trade-off")
                    .isEmpty();
            return;
        }

        // With >= 1 outlier, the trade-off must be present (7.5: never dropped silently).
        assertThat(success.tradeoff())
                .as("a detected outlier must surface the include-vs-exclude trade-off")
                .isPresent();
        OutlierTradeoff tradeoff = success.tradeoff().get();

        // The flagged outliers match the engine's own detection premise (7.1/7.2).
        assertThat(tradeoff.outliers()).isEqualTo(expectedOutliers);
        assertThat(tradeoff.outliers()).isNotEmpty();

        // The including field is exactly the top-level primary results: the outlier is
        // never dropped from the recommendation the Creator sees first (7.5).
        assertThat(tradeoff.including()).isSameAs(results);

        Set<ParticipantId> keptIds = new LinkedHashSet<>(allIds);
        keptIds.removeAll(expectedOutliers);

        // Both computations cover all three strategies (7.2): including keeps every
        // participant; excluding drops exactly the outlier(s) from every strategy.
        for (StrategyResult included : List.of(
                tradeoff.including().fastest(),
                tradeoff.including().minimax(),
                tradeoff.including().fairest())) {
            assertPointInRange(included.point());
            assertThat(included.perParticipant().keySet())
                    .as("including variant carries all participants for every strategy")
                    .isEqualTo(allIds);
        }
        for (StrategyResult excluded : List.of(
                tradeoff.excluding().fastest(),
                tradeoff.excluding().minimax(),
                tradeoff.excluding().fairest())) {
            assertPointInRange(excluded.point());
            assertThat(excluded.perParticipant().keySet())
                    .as("excluding variant omits exactly the outlier(s) for every strategy")
                    .isEqualTo(keptIds);
        }

        // Each computation reports a finite, non-negative group-average travel time (7.3).
        assertThat(tradeoff.avgTravelTimeIncluding()).isFinite().isGreaterThanOrEqualTo(0.0);
        assertThat(tradeoff.avgTravelTimeExcluding()).isFinite().isGreaterThanOrEqualTo(0.0);
    }

    private static void assertPointInRange(Coordinate point) {
        assertThat(point.lat()).isBetween(-90.0, 90.0);
        assertThat(point.lng()).isBetween(-180.0, 180.0);
    }

    // ---- Generators ----

    /**
     * Generates a meeting of {@code n} participants (3&ndash;10) with distinct
     * in-bounds origins, plus a {@link FakeRoutingProvider} that exaggerates one
     * chosen participant's travel times by a large factor so it is a strong
     * outlier candidate under the configured {@code MedianMultiple(2.0)} rule.
     * Using 3+ participants guarantees at least two non-outliers remain after
     * excluding one.
     */
    @Provide
    Arbitrary<Scenario> scenarios() {
        Arbitrary<Integer> counts = Arbitraries.integers().between(3, 10);
        // Large, deterministic exaggeration so the injected participant reliably
        // exceeds 2x the group median on the Fastest winner's vector.
        Arbitrary<Double> factors = Arbitraries.doubles().between(10.0, 50.0);
        // Seed picks distinct lattice cells for origins to keep coordinates unique.
        Arbitrary<Long> seeds = Arbitraries.longs().between(0L, Long.MAX_VALUE / 2);
        // Which participant (by index) is exaggerated.
        Arbitrary<Integer> outlierPicks = Arbitraries.integers().between(0, 9);

        return Combinators.combine(counts, factors, seeds, outlierPicks)
                .as(Scenario::build);
    }

    private record Scenario(MeetingInput meeting, EngineConfig config, FakeRoutingProvider routing) {

        static Scenario build(int count, double factor, long seed, int outlierPick) {
            List<ParticipantInput> participants = new ArrayList<>(count);
            Set<Coordinate> used = new LinkedHashSet<>();
            long s = seed;
            for (int i = 0; i < count; i++) {
                Coordinate location;
                do {
                    // Deterministic lattice strictly inside BOUNDS: 20x20 distinct cells.
                    // floorMod keeps cells in [0, 19] even when the LCG state is negative,
                    // so every generated coordinate stays within the service bounds.
                    int latCell = Math.floorMod(s, 20);
                    int lngCell = Math.floorMod(Math.floorDiv(s, 20), 20);
                    double lat = 6.05 + latCell * 0.02;   // in [6.05, 6.43] ⊂ [6.00, 6.50]
                    double lng = -75.75 + lngCell * 0.02; // in [-75.75, -75.37] ⊂ [-75.80, -75.30]
                    location = new Coordinate(lat, lng);
                    s = s * 6364136223846793005L + 1442695040888963407L; // LCG advance
                } while (!used.add(location));
                participants.add(new ParticipantInput(new ParticipantId("p" + i), "p" + i, location));
            }

            EngineConfig config = new EngineConfig(
                    BOUNDS, 25, 15_000.0, new OutlierRule.MedianMultiple(2.0), 0.5);

            int outlierIndex = outlierPick % count;
            Coordinate outlierOrigin = participants.get(outlierIndex).location();
            FakeRoutingProvider routing = FakeRoutingProvider.builder()
                    .withOutlierOrigin(outlierOrigin, factor)
                    .build();

            MeetingInput meeting =
                    new MeetingInput(new ArrayList<>(participants), TransportMode.DRIVING);
            return new Scenario(meeting, config, routing);
        }
    }
}
