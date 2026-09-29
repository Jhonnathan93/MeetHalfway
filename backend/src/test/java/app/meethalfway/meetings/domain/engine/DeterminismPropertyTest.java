package app.meethalfway.meetings.domain.engine;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Feature: meeting-recommendation-engine, Property 10: Determinism
 *
 * <p>For any meeting input, engine configuration, and routing behavior, running
 * {@link RecommendationEngine#compute} twice with the <em>same</em>
 * input, config, and routing provider produces identical results for all three
 * strategies (identical selected points and metrics). The engine's collaborators
 * — grid candidate generation, metric computation, ε-tolerant tie-breaking with a
 * final centroid-distance tie-break, and outlier dual-computation — are all pure
 * functions of their inputs, and the {@link FakeRoutingProvider} is a
 * deterministic pure function of {@code (origin, destination, mode)}, so two runs
 * must be observationally indistinguishable.
 *
 * <p>Validates: Requirements 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 3.4, 4.4, 4.5,
 * 4.6, 4.7.
 *
 * <p>Each scenario is generated as a coherent whole so it is always valid: an
 * arbitrary {@link ServiceBounds}, 2&ndash;10 participant origins constrained to
 * lie within those bounds (via fraction-based interpolation, matching the grid
 * test's approach), a shared {@link TransportMode}, and a valid
 * {@link EngineConfig}. A deterministic {@link FakeRoutingProvider} is used —
 * either the plain distance model or one with an injected outlier origin — so the
 * outlier dual-computation path (and its trade-off equality) is exercised too.
 *
 * <p>Because {@link RecommendationOutcome} and every record it carries
 * ({@link StrategyResults}, {@link StrategyResult}, {@link OutlierTradeoff}, with
 * value-based {@code Map}/{@code Optional} fields) use value equality, full
 * outcome equality captures identical points and metrics. The test asserts full
 * equality and additionally makes the per-strategy point/metric equality explicit
 * so a determinism regression names the offending strategy.
 */
class DeterminismPropertyTest {

    private final RecommendationEngine engine =
            new RecommendationEngine(
                    new MeetingValidator(),
                    new GridCandidateGenerator(),
                    new MetricCalculator(),
                    new OutlierDetector());

    /**
     * Property 10 &mdash; two computations over the same input, config, and
     * deterministic routing yield equal outcomes, with identical selected points
     * and metrics for the Fastest, Minimax, and Fairest strategies (and identical
     * outlier trade-offs when present).
     */
    @Property(tries = 100)
    void twoRunsProduceIdenticalStrategyPointsAndMetrics(@ForAll("scenarios") Scenario scenario) {
        MeetingInput input = scenario.input();
        EngineConfig config = scenario.config();
        FakeRoutingProvider routing = scenario.routing();

        RecommendationOutcome first = engine.compute(input, config, routing);
        RecommendationOutcome second = engine.compute(input, config, routing);

        // Full value equality: identical variant, identical results, identical
        // (or identically-empty) outlier trade-off.
        assertThat(second).isEqualTo(first);

        // Both runs must reach the same branch; the property targets successful
        // computations (the deterministic fake never fails for these origins).
        assertThat(first).isInstanceOf(RecommendationOutcome.Success.class);
        assertThat(second).isInstanceOf(RecommendationOutcome.Success.class);

        RecommendationOutcome.Success firstSuccess = (RecommendationOutcome.Success) first;
        RecommendationOutcome.Success secondSuccess = (RecommendationOutcome.Success) second;

        assertStrategyResultsEqual(firstSuccess.results(), secondSuccess.results());

        // The outlier trade-off (present or absent) must match run-to-run, and
        // when present each of its including/excluding variants is identical too.
        assertThat(secondSuccess.tradeoff().isPresent())
                .isEqualTo(firstSuccess.tradeoff().isPresent());
        if (firstSuccess.tradeoff().isPresent()) {
            OutlierTradeoff firstTradeoff = firstSuccess.tradeoff().get();
            OutlierTradeoff secondTradeoff = secondSuccess.tradeoff().get();
            assertThat(secondTradeoff.outliers()).isEqualTo(firstTradeoff.outliers());
            assertThat(secondTradeoff.avgTravelTimeIncluding())
                    .isEqualTo(firstTradeoff.avgTravelTimeIncluding());
            assertThat(secondTradeoff.avgTravelTimeExcluding())
                    .isEqualTo(firstTradeoff.avgTravelTimeExcluding());
            assertStrategyResultsEqual(firstTradeoff.including(), secondTradeoff.including());
            assertStrategyResultsEqual(firstTradeoff.excluding(), secondTradeoff.excluding());
        }
    }

    /** Asserts identical selected point and metrics for every strategy. */
    private static void assertStrategyResultsEqual(StrategyResults first, StrategyResults second) {
        assertStrategyResultEqual(first.fastest(), second.fastest());
        assertStrategyResultEqual(first.minimax(), second.minimax());
        assertStrategyResultEqual(first.fairest(), second.fairest());
    }

    /** Asserts identical selected point, per-participant times, and metrics. */
    private static void assertStrategyResultEqual(StrategyResult first, StrategyResult second) {
        assertThat(second.point()).isEqualTo(first.point());
        assertThat(second.perParticipant()).isEqualTo(first.perParticipant());
        assertThat(second.sumTime()).isEqualTo(first.sumTime());
        assertThat(second.maxTime()).isEqualTo(first.maxTime());
        assertThat(second.stdDev()).isEqualTo(first.stdDev());
    }

    // ---- Generators ----

    @Provide
    Arbitrary<Scenario> scenarios() {
        return serviceBounds()
                .flatMap(
                        bounds ->
                                Combinators.combine(
                                                originsWithin(bounds),
                                                Arbitraries.of(TransportMode.class),
                                                Arbitraries.integers().between(1, 40),
                                                Arbitraries.doubles().between(1.0, 50_000.0),
                                                outlierRules(),
                                                Arbitraries.doubles().between(0.1, 5.0),
                                                Arbitraries.integers().between(0, 3))
                                        .as(
                                                (origins, mode, n, radius, rule, epsilon, outlierPick) -> {
                                                    EngineConfig config =
                                                            new EngineConfig(bounds, n, radius, rule, epsilon);
                                                    MeetingInput input = meeting(origins, mode);
                                                    FakeRoutingProvider routing =
                                                            routingFor(origins, outlierPick);
                                                    return new Scenario(input, config, routing);
                                                }));
    }

    /**
     * Picks a deterministic routing provider. {@code outlierPick == 0} uses the
     * plain distance model; otherwise one origin is turned into an exaggerated
     * outlier so the outlier dual-computation path is exercised and its trade-off
     * equality is checked. Either way the provider is a pure function of its
     * inputs, so both engine runs see the same travel-time matrix.
     */
    private static FakeRoutingProvider routingFor(List<Coordinate> origins, int outlierPick) {
        if (outlierPick <= 0) {
            return FakeRoutingProvider.withDefaults();
        }
        Coordinate exaggerated = origins.get(outlierPick % origins.size());
        return FakeRoutingProvider.builder().withOutlierOrigin(exaggerated, 25.0).build();
    }

    private static MeetingInput meeting(List<Coordinate> origins, TransportMode mode) {
        List<ParticipantInput> participants = new ArrayList<>(origins.size());
        for (int i = 0; i < origins.size(); i++) {
            String id = "p" + i;
            participants.add(new ParticipantInput(new ParticipantId(id), id, origins.get(i)));
        }
        return new MeetingInput(participants, mode);
    }

    /**
     * Builds an arbitrary service-bounds box with a non-degenerate span on each
     * axis, kept comfortably inside the universal coordinate ranges so origins
     * generated within it are always constructible.
     */
    private static Arbitrary<ServiceBounds> serviceBounds() {
        Arbitrary<Double> minLat = Arbitraries.doubles().between(-89.0, 88.0).ofScale(6);
        Arbitrary<Double> minLng = Arbitraries.doubles().between(-179.0, 178.0).ofScale(6);
        Arbitrary<Double> latSpan = Arbitraries.doubles().between(0.0001, 1.0).ofScale(6);
        Arbitrary<Double> lngSpan = Arbitraries.doubles().between(0.0001, 1.0).ofScale(6);
        return Combinators.combine(minLat, minLng, latSpan, lngSpan)
                .as(
                        (loLat, loLng, dLat, dLng) ->
                                new ServiceBounds(
                                        loLat,
                                        Math.min(90.0, loLat + dLat),
                                        loLng,
                                        Math.min(180.0, loLng + dLng)));
    }

    /**
     * Generates 2&ndash;10 origins whose coordinates all fall within the given
     * service bounds, so meeting validation and the generator's precondition
     * (origins inside bounds) are satisfied.
     */
    private static Arbitrary<List<Coordinate>> originsWithin(ServiceBounds bounds) {
        Arbitrary<Double> fraction = Arbitraries.doubles().between(0.0, 1.0).ofScale(6);
        Arbitrary<Coordinate> coordinate =
                Combinators.combine(fraction, fraction)
                        .as(
                                (latFraction, lngFraction) ->
                                        new Coordinate(
                                                lerp(bounds.minLat(), bounds.maxLat(), latFraction),
                                                lerp(bounds.minLng(), bounds.maxLng(), lngFraction)));
        return coordinate.list().ofMinSize(2).ofMaxSize(10);
    }

    /** Linear interpolation clamped so the result never drifts outside [min, max]. */
    private static double lerp(double min, double max, double fraction) {
        double value = min + (max - min) * fraction;
        return Math.max(min, Math.min(max, value));
    }

    private static Arbitrary<OutlierRule> outlierRules() {
        Arbitrary<OutlierRule> medianMultiple =
                Arbitraries.doubles().between(0.1, 10.0).map(OutlierRule::new);
        return medianMultiple;
    }

    /** A coherent, always-valid determinism scenario. */
    record Scenario(MeetingInput input, EngineConfig config, FakeRoutingProvider routing) {
    }
}
