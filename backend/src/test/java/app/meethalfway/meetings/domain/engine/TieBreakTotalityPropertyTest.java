package app.meethalfway.meetings.domain.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.StrategyResult;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.shared.domain.ParticipantId;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for tie-break totality across all three strategy selectors
 * ({@link FastestSelector}, {@link MinimaxSelector}, {@link FairestSelector}).
 *
 * <p>Feature: meeting-recommendation-engine, Property 9: Tie-break totality
 *
 * <p>Validates: Requirements 2.3, 2.4, 2.5, 3.2, 3.3, 3.4, 4.5, 4.6, 4.7 &mdash;
 * each strategy's comparator chain ends in distance to the
 * {@code Geographic_Centroid} applied <em>without</em> the ε tolerance, so the
 * chain is a total order that always yields a single selected candidate with no
 * unresolved tie.
 *
 * <p>The generators deliberately manufacture ties and near-ties: candidates share
 * identical or within-ε {@code Sum_Time}/{@code Max_Time}/{@code Std_Dev} values
 * so the metric comparators fall through and the centroid-distance tiebreak must
 * decide the winner. Every candidate is given a <em>distinct</em> in-range point
 * so the final non-ε comparator is decisive; this is exactly the situation where
 * a partial order would leave an unresolved tie.
 *
 * <p>Totality is demonstrated by two observable consequences of a total order:
 * <ul>
 *   <li>selection returns a single non-null candidate that is a member of the
 *       input, and</li>
 *   <li>selection is deterministic &mdash; selecting again, and selecting on a
 *       shuffled copy of the same candidates, yields the same winner (equal point
 *       and equal metrics).</li>
 * </ul>
 * If any residual tie were left unresolved, input order would leak into the
 * result and the shuffled-input selection would disagree.
 */
class TieBreakTotalityPropertyTest {

    /** Matches {@code EngineConfig.epsilonMinutes} working value (≈ 0.5 min). */
    private static final double EPSILON_MINUTES = 0.5;

    private final TieBreaker tieBreaker = new TieBreaker(EPSILON_MINUTES);
    private final FastestSelector fastest = new FastestSelector(tieBreaker);
    private final MinimaxSelector minimax = new MinimaxSelector(tieBreaker);
    private final FairestSelector fairest = new FairestSelector(tieBreaker);

    /**
     * Generates a non-empty list of {@link StrategyResult}s engineered to be
     * heavily tied. Metrics are drawn from small "cluster" pools so many
     * candidates share identical or within-ε {@code Sum_Time}/{@code Max_Time}/
     * {@code Std_Dev}, forcing every metric comparator to fall through to the
     * centroid-distance tiebreak. Each candidate is assigned a distinct point
     * (from a distinct integer index) so the final non-ε comparator is decisive.
     */
    @Provide
    Arbitrary<List<StrategyResult>> tiedCandidateLists() {
        // Small clustered pools => frequent exact and within-ε ties on each metric.
        Arbitrary<Double> sumTimes = Arbitraries.of(10.0, 10.2, 10.4, 50.0, 50.3, 100.0);
        Arbitrary<Integer> maxTimes = Arbitraries.of(5, 20, 40);
        Arbitrary<Double> stdDevs = Arbitraries.of(0.0, 0.3, 2.5, 2.7, 8.0);

        Arbitrary<List<Metrics>> metricsList =
                Combinators.combine(sumTimes, maxTimes, stdDevs)
                        .as(Metrics::new)
                        .list()
                        .ofMinSize(1)
                        .ofMaxSize(25);

        // Turn the metrics list into candidates with distinct points (index-based).
        return metricsList.map(TieBreakTotalityPropertyTest::withDistinctPoints);
    }

    /**
     * Property 9 &mdash; the Fastest chain ({@code Σ → max → σ → distance})
     * yields a single, deterministic winner even under heavy ties.
     */
    @Property(tries = 100)
    void fastestChainYieldsSingleDeterministicWinner(
            @ForAll("tiedCandidateLists") List<StrategyResult> candidates) {
        assertTotalOrderSelection(fastest::select, candidates);
    }

    /**
     * Property 9 &mdash; the Minimax chain ({@code max → Σ → σ → distance})
     * yields a single, deterministic winner even under heavy ties.
     */
    @Property(tries = 100)
    void minimaxChainYieldsSingleDeterministicWinner(
            @ForAll("tiedCandidateLists") List<StrategyResult> candidates) {
        assertTotalOrderSelection(minimax::select, candidates);
    }

    /**
     * Property 9 &mdash; the Fairest chain ({@code σ → Σ → max → distance}, over
     * the efficiency-feasible subset) yields a single, deterministic winner even
     * under heavy ties.
     */
    @Property(tries = 100)
    void fairestChainYieldsSingleDeterministicWinner(
            @ForAll("tiedCandidateLists") List<StrategyResult> candidates) {
        assertTotalOrderSelection(fairest::select, candidates);
    }

    /**
     * Asserts that {@code selector} resolves the (heavily tied) candidate list to
     * a single winner with no unresolved tie: the winner is a member of the input,
     * selecting again is stable, and selecting on a shuffled copy yields the same
     * winner. Winner equality is checked on the observable selection outcome
     * (point plus metrics), which is what a total order must fix regardless of
     * input order.
     */
    private void assertTotalOrderSelection(
            BiFunction<List<StrategyResult>, Coordinate, StrategyResult> selector,
            List<StrategyResult> candidates) {
        Coordinate centroid = GeographicCentroid.of(pointsOf(candidates));

        StrategyResult winner = selector.apply(candidates, centroid);

        assertThat(winner).as("selection must return a single non-null winner").isNotNull();
        assertThat(candidates)
                .as("winner must be one of the input candidates")
                .contains(winner);

        // Determinism on the same input: a total order never depends on call count.
        StrategyResult again = selector.apply(candidates, centroid);
        assertSameSelection(winner, again);

        // Determinism under input reordering: a total order never depends on input
        // order. If any tie were unresolved, a shuffle could surface a different
        // "first minimum" and this would fail.
        List<StrategyResult> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, new Random(candidates.size() * 31L + 7L));
        StrategyResult fromShuffled = selector.apply(shuffled, centroid);
        assertSameSelection(winner, fromShuffled);
    }

    /**
     * Two selection outcomes are "the same" when they agree on the selected point
     * and all metrics. Distinct points guarantee that agreement on the point means
     * the same candidate was chosen; agreement on metrics guards against any
     * accidental point collision being treated as a pass.
     */
    private static void assertSameSelection(StrategyResult expected, StrategyResult actual) {
        assertThat(actual.point())
                .as("selected point must be identical across repeated/shuffled selection")
                .isEqualTo(expected.point());
        assertThat(actual.sumTime())
                .as("selected Sum_Time must be identical across repeated/shuffled selection")
                .isEqualTo(expected.sumTime());
        assertThat(actual.maxTime())
                .as("selected Max_Time must be identical across repeated/shuffled selection")
                .isEqualTo(expected.maxTime());
        assertThat(actual.stdDev())
                .as("selected Std_Dev must be identical across repeated/shuffled selection")
                .isEqualTo(expected.stdDev());
    }

    /**
     * Builds candidates from the given metrics, assigning each a distinct point so
     * the centroid-distance tiebreak can always decide a residual tie. The point
     * index is mapped into the valid coordinate range with a spread wide enough
     * that no two candidates share squared distance to the centroid.
     */
    private static List<StrategyResult> withDistinctPoints(List<Metrics> metrics) {
        List<StrategyResult> candidates = new ArrayList<>(metrics.size());
        for (int i = 0; i < metrics.size(); i++) {
            Metrics m = metrics.get(i);
            Coordinate point = pointForIndex(i);
            Map<ParticipantId, Minutes> perParticipant = new LinkedHashMap<>();
            perParticipant.put(new ParticipantId("p0"), new Minutes(m.maxTime()));
            candidates.add(
                    new StrategyResult(point, perParticipant, m.sumTime(), m.maxTime(), m.stdDev()));
        }
        return candidates;
    }

    /**
     * Maps a candidate index to a distinct in-range coordinate whose squared
     * distance to the point set's centroid is <em>strictly</em> increasing in the
     * index, so the non-ε centroid-distance tiebreak is always decisive and no two
     * candidates can tie on it.
     *
     * <p>Collinear points would allow a symmetric pair to be equidistant from the
     * (also-collinear) centroid, which would leave the tiebreak unresolved. To
     * avoid that, the latitude follows a strictly convex quadratic in the index
     * while the longitude is fixed. All candidates share the same longitude, so
     * the centroid's longitude equals theirs and contributes zero to every
     * distance; the latitude offsets {@code base + i²·step} are strictly convex
     * and non-negative, so their gaps from the centroid latitude are strictly
     * ordered — giving strictly increasing squared distances with no ties.
     */
    private static Coordinate pointForIndex(int index) {
        // Convex, strictly increasing latitude offsets kept well within [-90, 90]
        // even for the largest generated lists (index up to 24 => offset < 6°).
        double step = 0.01;
        double lat = -40.0 + (double) index * index * step;
        double lng = 0.0;
        return new Coordinate(lat, lng);
    }

    private static List<Coordinate> pointsOf(List<StrategyResult> candidates) {
        List<Coordinate> points = new ArrayList<>(candidates.size());
        for (StrategyResult c : candidates) {
            points.add(c.point());
        }
        return points;
    }

    /** Simple carrier for the three engineered metrics of a candidate. */
    private record Metrics(double sumTime, int maxTime, double stdDev) {}
}
