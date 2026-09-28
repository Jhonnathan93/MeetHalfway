package app.meethalfway.meetings.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.meetings.domain.model.DomainConstants;
import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EvaluatedCandidate;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.shared.domain.ParticipantId;
import java.util.ArrayList;
import java.util.Comparator;
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
 * {@code Std_Dev (σ)}. Concretely, the selected Fairest result is optimal within
 * the feasible set ({@code Σ ≤ (1 + EFFICIENCY_TOLERANCE) · T* + ε}): no feasible
 * candidate {@code c} is preferred over the winner under the selector's own
 * ε-tolerant total order {@code σ → Σ → max → distance → lat/lng}.
 *
 * <p>The property is expressed against the exact comparator the engine uses
 * rather than a standalone {@code Std_Dev} inequality. The naive form
 * "{@code c.stdDev() >= winner.stdDev() - ε}" is <em>not</em> the invariant the
 * selector guarantees: because {@link TieBreaker#areEqual(double, double)} treats
 * a {@code σ} difference of {@code < ε} as a tie, the winner may legitimately be
 * chosen with a {@code σ} up to (strictly less than) ε above another feasible
 * candidate's {@code σ} when the near-tie is resolved on a later key
 * ({@code Σ}, {@code max}, centroid distance, exact point). Comparing raw
 * {@code σ} values with a non-strict {@code >=} at that exact boundary yields
 * false counterexamples on certain random seeds. Reproducing the selector's
 * feasible-set restriction and {@link TieBreaker#totalOrder} comparator and
 * asserting {@code order.compare(winner, c) <= 0} encodes "winner is the
 * selector's optimum within the feasible set" precisely and is seed-independent
 * by construction.
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
     * Property 8 &mdash; the selected Fairest winner is optimal within the
     * feasible set: for every candidate {@code c} in the feasible set
     * ({@code Σ ≤ (1 + EFFICIENCY_TOLERANCE) · T* + ε}), the winner is not strictly
     * worse than {@code c} under the selector's own ε-tolerant total order
     * {@code σ → Σ → max → distance → lat/lng}, i.e. {@code order.compare(winner, c) <= 0}.
     *
     * <p>This reproduces {@link FairestSelector}'s feasible-set restriction and
     * {@link TieBreaker#totalOrder} comparator exactly, so the assertion matches
     * the engine's real selection semantics and cannot produce a false
     * counterexample on any seed.
     */
    @Property(tries = 100)
    void winnerIsOptimalWithinFeasibleSetUnderSelectorTotalOrder(
            @ForAll("candidateLists") List<EvaluatedCandidate> candidates) {

        Coordinate centroid = GeographicCentroid.of(pointsOf(candidates));

        EvaluatedCandidate winner = selector.select(candidates, centroid);

        // Reproduce the selector's exact ε-tolerant total order (Req 4.4–4.7):
        // σ → Σ → max → distance-to-centroid → exact lat/lng.
        Comparator<EvaluatedCandidate> order =
                tieBreaker.totalOrder(
                        centroid,
                        List.of(
                                tieBreaker.byStdDev(),
                                tieBreaker.bySumTime(),
                                tieBreaker.byMaxTime()));

        double feasibilityThreshold =
                (1.0 + DomainConstants.EFFICIENCY_TOLERANCE) * minSumTime(candidates)
                        + EPSILON_MINUTES;

        for (EvaluatedCandidate c : candidates) {
            if (c.sumTime() <= feasibilityThreshold) {
                assertThat(order.compare(winner, c))
                        .as("winner must not be strictly worse than any feasible candidate "
                                + "under the selector's ε-tolerant total order")
                        .isLessThanOrEqualTo(0);
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
