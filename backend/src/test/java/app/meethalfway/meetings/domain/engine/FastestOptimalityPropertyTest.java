package app.meethalfway.meetings.domain.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EvaluatedCandidate;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.shared.domain.ParticipantId;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for {@link FastestSelector} optimality.
 *
 * <p>Feature: meeting-recommendation-engine, Property 5: Fastest optimality
 *
 * <p>Validates: Requirements 2.2 &mdash; the Fastest strategy minimizes the
 * group's total travel time {@code Sum_Time (Σ tᵢ)}. "No candidate has a
 * {@code Sum_Time} strictly lower than the Fastest result beyond ε" is asserted
 * as the selected winner being ε-optimal: its {@code Sum_Time} is within ε of the
 * lowest {@code Sum_Time} present in the candidate set, i.e.
 * {@code winner.sumTime() <= min(c.sumTime()) + ε}.
 *
 * <p>Candidates are generated the way the engine builds them (task 4.2): each
 * candidate's {@code Sum_Time}/{@code Max_Time}/{@code Std_Dev} are derived by
 * {@link MetricCalculator} from a single per-participant travel-time vector, so
 * {@code Σ} moves together with {@code max} and {@code σ}. This matters because
 * ε-tolerant {@code Σ}-equality is not transitive: with metrics drawn
 * independently, a higher-{@code Σ} candidate with an artificially low
 * {@code max} could leapfrog a lower-{@code Σ} one on the secondary key, a
 * candidate shape the engine can never construct. Generating metric-consistent
 * candidates keeps the property faithful to the real input space, and under it
 * the winner is provably within ε of the minimum {@code Σ}.
 *
 * <p>The ε tolerance is the same {@code epsilonMinutes} carried by the
 * {@link TieBreaker} that backs the selector, so the assertion uses exactly the
 * threshold the engine applies when treating two {@code Sum_Time} values as tied.
 */
class FastestOptimalityPropertyTest {

    /** Matches {@code EngineConfig.epsilonMinutes} working value (≈ 0.5 min). */
    private static final double EPSILON_MINUTES = 0.5;

    /** Fixed participant set shared by every candidate in a generated list. */
    private static final List<ParticipantId> PARTICIPANTS = List.of(
            new ParticipantId("p0"),
            new ParticipantId("p1"),
            new ParticipantId("p2"));

    private final TieBreaker tieBreaker = new TieBreaker(EPSILON_MINUTES);
    private final MetricCalculator metricCalculator = new MetricCalculator();
    private final FastestSelector selector = new FastestSelector(tieBreaker);

    /**
     * Generates a non-empty list (1&ndash;20) of <em>metric-consistent</em>
     * {@link EvaluatedCandidate}s, mirroring how the engine actually produces
     * candidates: each candidate's {@code Sum_Time}/{@code Max_Time}/{@code Std_Dev}
     * are derived by {@link MetricCalculator} from a single whole-minute
     * per-participant travel-time vector (Requirement 5.2), not drawn
     * independently. Deriving the metrics from one vector keeps {@code Σ} in a
     * fixed relationship with {@code max} and {@code σ}, which is the input space
     * the Fastest selector operates on in production; feeding independently drawn
     * metrics would exercise candidate shapes the engine can never construct.
     *
     * <p>All candidates in a list share the same participant set and each carries
     * an in-range point.
     */
    @Provide
    Arbitrary<List<EvaluatedCandidate>> candidateLists() {
        Arbitrary<Integer> lats = Arbitraries.integers().between(-90, 90);
        Arbitrary<Integer> lngs = Arbitraries.integers().between(-180, 180);
        Arbitrary<List<Integer>> travelTimes =
                Arbitraries.integers().between(0, 600).list().ofSize(PARTICIPANTS.size());

        Arbitrary<EvaluatedCandidate> candidate =
                Combinators.combine(lats, lngs, travelTimes)
                        .as((lat, lng, times) -> {
                            Map<ParticipantId, Minutes> perParticipant = new LinkedHashMap<>();
                            for (int i = 0; i < PARTICIPANTS.size(); i++) {
                                perParticipant.put(PARTICIPANTS.get(i), new Minutes(times.get(i)));
                            }
                            return metricCalculator.evaluate(new Coordinate(lat, lng), perParticipant);
                        });

        return candidate.list().ofMinSize(1).ofMaxSize(20);
    }

    /**
     * Property 5 &mdash; the selected Fastest winner is ε-optimal on
     * {@code Sum_Time}: its {@code Sum_Time} does not exceed the minimum
     * {@code Sum_Time} in the candidate set by more than ε, i.e. no candidate has a
     * {@code Sum_Time} strictly lower than the winner beyond ε.
     */
    @Property(tries = 100)
    void noCandidateHasStrictlyLowerSumTimeBeyondEpsilon(
            @ForAll("candidateLists") List<EvaluatedCandidate> candidates) {

        Coordinate centroid = GeographicCentroid.of(pointsOf(candidates));

        EvaluatedCandidate winner = selector.select(candidates, centroid);

        double minimumSumTime = candidates.stream()
                .mapToDouble(EvaluatedCandidate::sumTime)
                .min()
                .orElseThrow();

        assertThat(winner.sumTime())
                .as("Fastest winner Sum_Time must be within ε of the minimum candidate Sum_Time")
                .isLessThanOrEqualTo(minimumSumTime + EPSILON_MINUTES);
    }

    private static List<Coordinate> pointsOf(List<EvaluatedCandidate> candidates) {
        List<Coordinate> points = new ArrayList<>(candidates.size());
        for (EvaluatedCandidate c : candidates) {
            points.add(c.point());
        }
        return points;
    }
}
