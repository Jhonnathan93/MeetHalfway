package app.meethalfway.domain.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.domain.DomainConstants;
import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.EvaluatedCandidate;
import app.meethalfway.domain.model.Minutes;
import app.meethalfway.domain.model.ParticipantId;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Feature: meeting-recommendation-engine, Property 7: Fairest feasibility invariant
 *
 * <p>For any non-empty set of evaluated candidates, the {@link FairestSelector}
 * winner's {@code Sum_Time (Σ)} never exceeds the {@code Fastest_Optimum T*}
 * inflated by the fixed 15% {@code Efficiency_Tolerance}, within the tie-breaker's
 * ε slack: {@code Σ_winner ≤ (1 + EFFICIENCY_TOLERANCE) · T* + ε}, where
 * {@code T* = min Σ} across all candidates. The Fairest strategy may never pick a
 * candidate that is more efficient-costly than this bound, regardless of how low
 * its {@code Std_Dev} is (Requirements 4.1, 4.2, 4.3).
 *
 * <p>Validates: Requirements 4.1, 4.2, 4.3.
 *
 * <p>{@code T*} is recomputed independently in this test as the minimum
 * {@code Sum_Time} over the generated candidates, so the assertion is not a
 * re-run of the selector's own feasibility computation.
 */
class FairestFeasibilityPropertyTest {

    private static final Coordinate CENTROID = new Coordinate(6.25, -75.55);
    private static final double EPSILON_MINUTES = 0.5;

    private final TieBreaker tieBreaker = new TieBreaker(EPSILON_MINUTES);
    private final FairestSelector selector = new FairestSelector(tieBreaker);

    /**
     * The Fairest winner's Σ stays within {@code 1.15 · T* + ε}, with T* the
     * independently-computed minimum Σ across all generated candidates.
     */
    @Property(tries = 100)
    void fairestWinnerRespectsEfficiencyFeasibilityBound(
            @ForAll("candidateLists") List<EvaluatedCandidate> candidates) {
        double tStar = candidates.stream()
                .mapToDouble(EvaluatedCandidate::sumTime)
                .min()
                .orElseThrow();

        double bound = (1.0 + DomainConstants.EFFICIENCY_TOLERANCE) * tStar + EPSILON_MINUTES;

        EvaluatedCandidate winner = selector.select(candidates, CENTROID);

        assertThat(winner.sumTime()).isLessThanOrEqualTo(bound);
    }

    // ---- Generators ----

    @Provide
    Arbitrary<List<EvaluatedCandidate>> candidateLists() {
        return candidate().list().ofMinSize(1).ofMaxSize(30);
    }

    private Arbitrary<EvaluatedCandidate> candidate() {
        Arbitrary<Double> latitude = Arbitraries.doubles().between(6.0, 6.5).ofScale(6);
        Arbitrary<Double> longitude = Arbitraries.doubles().between(-75.7, -75.4).ofScale(6);
        Arbitrary<Double> sumTime = Arbitraries.doubles().between(0.0, 1000.0).ofScale(3);
        Arbitrary<Integer> maxTime = Arbitraries.integers().between(0, 240);
        Arbitrary<Double> stdDev = Arbitraries.doubles().between(0.0, 200.0).ofScale(3);

        return Combinators.combine(latitude, longitude, sumTime, maxTime, stdDev)
                .as((lat, lon, sum, max, sd) -> {
                    // EvaluatedCandidate calls containsKey(null)/containsValue(null),
                    // so use a mutable map that supports null-key lookups.
                    Map<ParticipantId, Minutes> perParticipant = new LinkedHashMap<>();
                    perParticipant.put(new ParticipantId("p1"), new Minutes(max));
                    return new EvaluatedCandidate(
                            new Coordinate(lat, lon), perParticipant, sum, max, sd);
                });
    }
}
