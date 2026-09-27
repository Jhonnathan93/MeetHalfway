package app.meethalfway.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.domain.DomainConstants;
import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.EvaluatedCandidate;
import app.meethalfway.domain.model.Minutes;
import app.meethalfway.domain.model.ParticipantId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for {@link FairestSelector} optimality within the feasible
 * set.
 *
 * <p>Feature: meeting-recommendation-engine, Property 8: Fairest optimality
 * within the feasible set
 *
 * <p>Validates: Requirements 4.4 &mdash; the Fairest strategy, among the
 * candidates whose total travel time stays within the {@code Efficiency_Tolerance}
 * of the optimum (the feasible set), minimizes the spread of travel times
 * {@code Std_Dev (σ)}. Concretely, no candidate in the feasible set
 * ({@code Σ ≤ 1.15 · T*}) has a {@code Std_Dev} strictly lower than the selected
 * Fairest result beyond the ε tolerance: for every feasible candidate {@code c},
 * {@code c.stdDev() >= winner.stdDev() - ε}.
 *
 * <p>To keep the test's notion of the feasible set identical to the engine's,
 * the same {@link TieBreaker} epsilon is reused and the feasibility boundary is
 * computed with the exact expression {@link FairestSelector} applies:
 * {@code Σ ≤ (1 + EFFICIENCY_TOLERANCE) · T* + ε}, where {@code T*} is the minimum
 * {@code Sum_Time} across all candidates.
 */
class FairestOptimalityPropertyTest {

    /** Matches {@code EngineConfig.epsilonMinutes} working value (≈ 0.5 min). */
    private static final double EPSILON_MINUTES = 0.5;

    private final TieBreaker tieBreaker = new TieBreaker(EPSILON_MINUTES);
    private final FairestSelector selector = new FairestSelector(tieBreaker);

    /**
     * Generates a non-empty list (1&ndash;20) of {@link EvaluatedCandidate}s with
     * arbitrary but invariant-respecting metrics. Each candidate carries a
     * distinct in-range point and a small non-empty per-participant travel-time
     * map; the {@code Sum_Time}/{@code Max_Time}/{@code Std_Dev} metrics are drawn
     * independently within their valid ranges since Property 8 constrains only
     * the {@code Std_Dev} ordering within the {@code Sum_Time}-defined feasible
     * set, not metric internal consistency (that is Property 4's concern).
     */
    @Provide
    Arbitrary<List<EvaluatedCandidate>> candidateLists() {
        Arbitrary<Double> sumTimes = Arbitraries.doubles().between(0.0, 5000.0);
        Arbitrary<Integer> maxTimes = Arbitraries.integers().between(0, 600);
        Arbitrary<Double> stdDevs = Arbitraries.doubles().between(0.0, 1000.0);
        Arbitrary<Integer> lats = Arbitraries.integers().between(-90, 90);
        Arbitrary<Integer> lngs = Arbitraries.integers().between(-180, 180);

        Arbitrary<EvaluatedCandidate> candidate =
                Combinators.combine(sumTimes, maxTimes, stdDevs, lats, lngs)
                        .as((sum, max, std, lat, lng) -> {
                            Map<ParticipantId, Minutes> perParticipant = new LinkedHashMap<>();
                            perParticipant.put(new ParticipantId("p0"), new Minutes(max));
                            return new EvaluatedCandidate(
                                    new Coordinate(lat, lng), perParticipant, sum, max, std);
                        });

        return candidate.list().ofMinSize(1).ofMaxSize(20);
    }

    /**
     * Property 8 &mdash; the selected Fairest winner has a {@code Std_Dev} that no
     * <em>feasible</em> candidate beats beyond ε; i.e. for every candidate
     * {@code c} in the feasible set ({@code Σ ≤ (1 + EFFICIENCY_TOLERANCE) · T* + ε}),
     * {@code c.stdDev()} is not strictly lower than {@code winner.stdDev() - ε}.
     */
    @Property(tries = 100)
    void noFeasibleCandidateHasStrictlyLowerStdDevBeyondEpsilon(
            @ForAll("candidateLists") List<EvaluatedCandidate> candidates) {

        Coordinate centroid = GeographicCentroid.of(pointsOf(candidates));

        EvaluatedCandidate winner = selector.select(candidates, centroid);

        double feasibilityThreshold =
                (1.0 + DomainConstants.EFFICIENCY_TOLERANCE) * minSumTime(candidates)
                        + EPSILON_MINUTES;

        for (EvaluatedCandidate c : candidates) {
            if (c.sumTime() <= feasibilityThreshold) {
                assertThat(c.stdDev())
                        .as("feasible candidate Std_Dev must not be strictly lower than winner beyond ε")
                        .isGreaterThanOrEqualTo(winner.stdDev() - EPSILON_MINUTES);
            }
        }
    }

    private static double minSumTime(List<EvaluatedCandidate> candidates) {
        double tStar = Double.POSITIVE_INFINITY;
        for (EvaluatedCandidate c : candidates) {
            if (c.sumTime() < tStar) {
                tStar = c.sumTime();
            }
        }
        return tStar;
    }

    private static List<Coordinate> pointsOf(List<EvaluatedCandidate> candidates) {
        List<Coordinate> points = new ArrayList<>(candidates.size());
        for (EvaluatedCandidate c : candidates) {
            points.add(c.point());
        }
        return points;
    }
}
