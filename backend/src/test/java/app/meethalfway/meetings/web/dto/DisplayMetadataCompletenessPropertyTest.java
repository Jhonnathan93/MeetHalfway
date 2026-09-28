package app.meethalfway.meetings.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.meethalfway.meetings.domain.model.RecommendationOutcome;
import app.meethalfway.meetings.domain.model.StrategyResult;
import app.meethalfway.meetings.domain.model.StrategyResults;
import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.shared.domain.ParticipantId;
import app.meethalfway.shared.web.dto.CoordinateResponse;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Feature: modular-architecture-refactor, Property 6: Display metadata is complete for every candidate point
 *
 * <p>For any successful recommendation, each returned strategy result carries
 * latitude, longitude, total time ({@code sumTime}), maximum single-participant
 * time ({@code maxTime}), standard deviation ({@code stdDev}), a per-participant
 * travel time for every included participant, a {@code candidateId} equal to that
 * result's strategy key ({@code fastest}, {@code minimax}, or {@code fairest}),
 * and exactly one point per strategy marked {@code recommended}.
 *
 * <p>Validates: Requirements 10.2, 10.3, 10.4.
 *
 * <p>The property drives {@link WebMapper#toRecommendationResponse} (the additive
 * display-metadata seam added in task 7.1) with generated valid
 * {@link RecommendationOutcome.Success} fixtures: 2&ndash;10 participants with
 * unique ids and in-range coordinates. Each strategy result is built with a
 * per-participant travel-time map covering every generated participant, and
 * {@code sumTime}/{@code maxTime}/{@code stdDev} are computed from that vector so
 * the fixture is internally consistent. The property then asserts every
 * component of Property 6 across all three mapped strategy results without
 * touching production code or other test classes.
 *
 * <p>Generators and helpers are private/inner to this class so concurrent tasks
 * (7.4, 7.5) writing in the same package do not collide on shared fixtures.
 */
class DisplayMetadataCompletenessPropertyTest {

    /** The three strategy keys, in {@link StrategyResults} declaration order. */
    private static final List<String> STRATEGY_KEYS = List.of("fastest", "minimax", "fairest");

    private final WebMapper mapper = new WebMapper();

    /**
     * Property 6 &mdash; mapping a valid success yields three complete strategy
     * results: each carries an in-range coordinate, all three aggregate metrics,
     * a per-participant time for every included participant (none omitted, none
     * extra), a {@code candidateId} equal to its strategy key, and is the single
     * {@code recommended} point for that strategy.
     */
    @Property(tries = 100)
    void everyStrategyResultCarriesCompleteDisplayMetadata(
            @ForAll("successes") RecommendationOutcome.Success success) {

        RecommendationResponse response = mapper.toRecommendationResponse(success);

        // The three strategy slots are present and map one-to-one to strategy keys.
        StrategyResultsResponse results = response.results();
        assertThat(results).isNotNull();
        Map<String, StrategyResultResponse> byKey = new LinkedHashMap<>();
        byKey.put("fastest", results.fastest());
        byKey.put("minimax", results.minimax());
        byKey.put("fairest", results.fairest());

        Set<ParticipantId> expectedIds = participantIds(success);
        Set<String> expectedIdValues = new java.util.HashSet<>();
        for (ParticipantId id : expectedIds) {
            expectedIdValues.add(id.value());
        }

        for (String key : STRATEGY_KEYS) {
            StrategyResultResponse result = byKey.get(key);

            // Present: an in-range candidate point is never excluded (Property 6
            // covers the all-in-range success case; exclusion is Property 7).
            assertThat(result)
                    .as("strategy result for key '%s' must be present", key)
                    .isNotNull();

            // Latitude and longitude are present and in range (Requirement 10.2, 10.6).
            CoordinateResponse point = result.point();
            assertThat(point).as("point for '%s'", key).isNotNull();
            assertThat(point.lat()).as("lat for '%s'", key).isBetween(-90.0, 90.0);
            assertThat(point.lng()).as("lng for '%s'", key).isBetween(-180.0, 180.0);

            // Aggregate metrics are present and non-negative (Requirement 10.2).
            assertThat(result.sumTime()).as("sumTime for '%s'", key).isGreaterThanOrEqualTo(0.0);
            assertThat(result.maxTime()).as("maxTime for '%s'", key).isGreaterThanOrEqualTo(0);
            assertThat(result.stdDev()).as("stdDev for '%s'", key).isGreaterThanOrEqualTo(0.0);

            // A per-participant time for EVERY included participant, none omitted,
            // none extra (Requirement 10.3).
            assertThat(result.perParticipant())
                    .as("perParticipant keys for '%s'", key)
                    .containsOnlyKeys(expectedIdValues.toArray(new String[0]));
            for (Integer minutes : result.perParticipant().values()) {
                assertThat(minutes).as("per-participant minutes for '%s'", key).isNotNull();
                assertThat(minutes).as("per-participant minutes for '%s'", key)
                        .isGreaterThanOrEqualTo(0);
            }

            // candidateId equals the strategy key (Requirement 10.2).
            assertThat(result.candidateId())
                    .as("candidateId for '%s'", key)
                    .isEqualTo(key);

            // Each strategy returns exactly one selected point, which is its
            // recommended point (Requirement 10.4).
            assertThat(result.recommended())
                    .as("recommended flag for '%s'", key)
                    .isTrue();
        }

        // Exactly one recommended point per strategy: three strategies, three
        // recommended points (Requirement 10.4).
        long recommendedCount = byKey.values().stream()
                .filter(r -> r != null && r.recommended())
                .count();
        assertThat(recommendedCount)
                .as("exactly one recommended point per strategy (three total)")
                .isEqualTo(3L);
    }

    private static Set<ParticipantId> participantIds(RecommendationOutcome.Success success) {
        // All three results are built over the same participant set by the
        // generator, so the fastest result's keys are the canonical id set.
        return new java.util.LinkedHashSet<>(success.results().fastest().perParticipant().keySet());
    }

    // ---- Generators (private/inner to this class) ----

    /**
     * Valid, all-in-range successes: 2&ndash;10 participants with unique ids and
     * a {@link StrategyResults} whose three {@link StrategyResult}s each cover the
     * full participant set with consistent aggregate metrics. No outlier trade-off
     * is attached (irrelevant to Property 6). Coordinates are strictly in range.
     */
    @Provide
    Arbitrary<RecommendationOutcome.Success> successes() {
        Arbitrary<Integer> counts = Arbitraries.integers().between(2, 10);
        return counts.flatMap(count -> {
            List<ParticipantId> ids = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                ids.add(new ParticipantId("p" + i));
            }
            // One time-vector per strategy plus one point per strategy.
            Arbitrary<List<Integer>> fastestTimes = timesFor(count);
            Arbitrary<List<Integer>> minimaxTimes = timesFor(count);
            Arbitrary<List<Integer>> fairestTimes = timesFor(count);
            Arbitrary<Coordinate> pointA = coordinateInRange();
            Arbitrary<Coordinate> pointB = coordinateInRange();
            Arbitrary<Coordinate> pointC = coordinateInRange();
            return Combinators.combine(
                            fastestTimes, minimaxTimes, fairestTimes, pointA, pointB, pointC)
                    .as((ftimes, mtimes, xtimes, pa, pb, pc) -> {
                        StrategyResult fastest = strategyResult(ids, ftimes, pa);
                        StrategyResult minimax = strategyResult(ids, mtimes, pb);
                        StrategyResult fairest = strategyResult(ids, xtimes, pc);
                        return RecommendationOutcome.Success.of(
                                new StrategyResults(fastest, minimax, fairest));
                    });
        });
    }

    /** A per-participant travel-time vector of whole non-negative minutes. */
    private Arbitrary<List<Integer>> timesFor(int count) {
        return Arbitraries.integers().between(0, 240).list().ofSize(count);
    }

    /**
     * Builds a {@link StrategyResult} covering every id with the given time
     * vector, computing {@code sumTime}/{@code maxTime}/{@code stdDev} (population
     * std-dev) from that vector so the fixture is internally consistent.
     */
    private StrategyResult strategyResult(
            List<ParticipantId> ids, List<Integer> times, Coordinate point) {
        Map<ParticipantId, Minutes> perParticipant = new LinkedHashMap<>();
        double sum = 0.0;
        int max = 0;
        for (int i = 0; i < ids.size(); i++) {
            int t = times.get(i);
            perParticipant.put(ids.get(i), new Minutes(t));
            sum += t;
            if (t > max) {
                max = t;
            }
        }
        double mean = sum / ids.size();
        double sumSquaredDeviations = 0.0;
        for (int t : times) {
            double deviation = t - mean;
            sumSquaredDeviations += deviation * deviation;
        }
        double stdDev = Math.sqrt(sumSquaredDeviations / ids.size());
        return new StrategyResult(point, perParticipant, sum, max, stdDev);
    }

    /**
     * A coordinate strictly inside the valid ranges (latitude {@code [-90, 90]},
     * longitude {@code [-180, 180]}), staying off the exact edges so mapping is
     * always in-range for Property 6.
     */
    private Arbitrary<Coordinate> coordinateInRange() {
        Arbitrary<Double> lat = Arbitraries.doubles().between(-89.0, 89.0).ofScale(6);
        Arbitrary<Double> lng = Arbitraries.doubles().between(-179.0, 179.0).ofScale(6);
        return Combinators.combine(lat, lng).as(Coordinate::new);
    }
}
