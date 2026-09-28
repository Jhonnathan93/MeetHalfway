package app.meethalfway.meetings.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EvaluatedCandidate;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.shared.domain.ParticipantId;
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
 * Property-based test for {@link MinimaxSelector} optimality.
 *
 * <p>Feature: meeting-recommendation-engine, Property 6: Minimax optimality
 *
 * <p>Validates: Requirements 3.1 &mdash; the Minimax strategy minimizes the
 * worst individual trip {@code Max_Time (max tᵢ)}. Concretely: for any set of
 * evaluated candidates, no candidate has a {@code Max_Time} strictly lower than
 * the selected Minimax result's {@code Max_Time} beyond the ε tolerance. That is,
 * for every candidate {@code c}, {@code c.maxTime() >= winner.maxTime() - ε}.
 *
 * <p>{@code Max_Time} is a whole-minute {@code int} on {@link EvaluatedCandidate}
 * while ε is a real number of minutes, so the comparison is performed in
 * {@code double} space. The tie-breaker's ε (see {@link TieBreaker}) means the
 * selector may prefer a candidate whose {@code Max_Time} is within ε of the true
 * minimum in order to win on a lower-priority key (Σ, σ, or centroid distance);
 * the ε slack in the assertion accounts for exactly that.
 */
class MinimaxOptimalityPropertyTest {

    /** ε tolerance in minutes shared by the tie-breaker and the assertion. */
    private static final double EPSILON_MINUTES = 0.5;

    private final MinimaxSelector selector = new MinimaxSelector(new TieBreaker(EPSILON_MINUTES));

    /**
     * Generates a non-empty list of evaluated candidates with arbitrary metrics.
     *
     * <p>Each candidate gets a distinct coordinate (so the centroid tiebreak can
     * always resolve a total order) and a single-participant travel-time map built
     * with a mutable {@link LinkedHashMap} (the {@link EvaluatedCandidate}
     * constructor probes the map with {@code containsKey(null)}, which an
     * immutable {@code Map.of} would reject). {@code sumTime}, {@code maxTime}, and
     * {@code stdDev} are generated independently across their valid ranges so the
     * optimality claim is exercised against unconstrained metric combinations, not
     * only internally consistent ones.
     */
    @Provide
    Arbitrary<List<EvaluatedCandidate>> candidateLists() {
        Arbitrary<Integer> sizes = Arbitraries.integers().between(1, 40);
        return sizes.flatMap(size -> candidate().list().ofSize(size));
    }

    private Arbitrary<EvaluatedCandidate> candidate() {
        Arbitrary<Double> lat = Arbitraries.doubles().between(-89.0, 89.0);
        Arbitrary<Double> lng = Arbitraries.doubles().between(-179.0, 179.0);
        Arbitrary<Double> sumTime = Arbitraries.doubles().between(0.0, 6000.0);
        Arbitrary<Integer> maxTime = Arbitraries.integers().between(0, 600);
        Arbitrary<Double> stdDev = Arbitraries.doubles().between(0.0, 300.0);
        return Combinators.combine(lat, lng, sumTime, maxTime, stdDev)
                .as((la, ln, sum, max, sd) -> {
                    Map<ParticipantId, Minutes> perParticipant = new LinkedHashMap<>();
                    perParticipant.put(new ParticipantId("p0"), new Minutes(max));
                    return new EvaluatedCandidate(new Coordinate(la, ln), perParticipant, sum, max, sd);
                });
    }

    /**
     * Property 6 &mdash; for any candidate list and any centroid, the Minimax
     * winner's {@code Max_Time} is minimal within ε: no candidate has a strictly
     * lower {@code Max_Time} beyond the ε tolerance.
     */
    @Property(tries = 100)
    void noCandidateHasStrictlyLowerMaxTimeBeyondEpsilon(
            @ForAll("candidateLists") List<EvaluatedCandidate> candidates) {

        List<Coordinate> points = new ArrayList<>();
        for (EvaluatedCandidate candidate : candidates) {
            points.add(candidate.point());
        }
        Coordinate centroid = GeographicCentroid.of(points);

        EvaluatedCandidate winner = selector.select(candidates, centroid);

        assertThat(candidates).contains(winner);
        for (EvaluatedCandidate candidate : candidates) {
            assertThat((double) candidate.maxTime())
                    .as(
                            "candidate maxTime %d must not be strictly lower than winner maxTime %d beyond epsilon %s",
                            candidate.maxTime(), winner.maxTime(), EPSILON_MINUTES)
                    .isGreaterThanOrEqualTo(winner.maxTime() - EPSILON_MINUTES);
        }
    }
}
