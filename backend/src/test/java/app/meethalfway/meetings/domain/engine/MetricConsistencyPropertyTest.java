package app.meethalfway.meetings.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EvaluatedCandidate;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.shared.domain.ParticipantId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based test for {@link MetricCalculator} metric computation.
 *
 * <p>Feature: meeting-recommendation-engine, Property 4: Metric consistency
 *
 * <p>Validates: Requirements 5.2 &mdash; for any candidate point and any set of
 * per-participant travel times, the candidate's {@code Sum_Time} equals the
 * arithmetic sum of those times, its {@code Max_Time} equals their maximum, and
 * its {@code Std_Dev} equals their standard deviation.
 *
 * <p>The expected {@code Std_Dev} is computed here as the <b>population</b>
 * standard deviation ({@code sqrt((1/n) · Σ (tᵢ − μ)²)}, dividing by {@code n},
 * not {@code n − 1}), matching {@link MetricCalculator}. Expected values are
 * derived independently from the same travel-time map and asserted with a small
 * floating tolerance to absorb division/sqrt rounding.
 */
class MetricConsistencyPropertyTest {

    private static final double TOLERANCE = 1e-9;

    private final MetricCalculator calculator = new MetricCalculator();

    /**
     * Generates a travel-time map for 1&ndash;10 participants, each with a
     * non-negative whole-minute travel time. Participant ids are unique.
     */
    @Provide
    Arbitrary<Map<ParticipantId, Minutes>> travelTimeMaps() {
        Arbitrary<Integer> counts = Arbitraries.integers().between(1, 10);
        return counts.flatMap(count ->
                Arbitraries.integers()
                        .between(0, 600)
                        .list()
                        .ofSize(count)
                        .map(times -> {
                            Map<ParticipantId, Minutes> map = new LinkedHashMap<>();
                            for (int i = 0; i < times.size(); i++) {
                                map.put(new ParticipantId("p" + i), new Minutes(times.get(i)));
                            }
                            return map;
                        }));
    }

    /**
     * Property 4 &mdash; for any point and any travel-time map, the evaluated
     * candidate's Sum_Time, Max_Time, and Std_Dev equal the arithmetic sum,
     * maximum, and population standard deviation of the travel times computed
     * independently.
     */
    @Property(tries = 100)
    void metricsMatchIndependentlyComputedSumMaxAndPopulationStdDev(
            @ForAll("travelTimeMaps") Map<ParticipantId, Minutes> perParticipant,
            @ForAll @IntRange(min = -90, max = 90) int lat,
            @ForAll @IntRange(min = -180, max = 180) int lng) {

        Coordinate point = new Coordinate(lat, lng);
        List<Integer> times = perParticipant.values().stream().map(Minutes::value).toList();

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

        EvaluatedCandidate candidate = calculator.evaluate(point, perParticipant);

        assertThat(candidate.sumTime()).isCloseTo(expectedSum, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(candidate.maxTime()).isEqualTo(expectedMax);
        assertThat(candidate.stdDev()).isCloseTo(expectedStdDev, org.assertj.core.data.Offset.offset(TOLERANCE));
    }
}
