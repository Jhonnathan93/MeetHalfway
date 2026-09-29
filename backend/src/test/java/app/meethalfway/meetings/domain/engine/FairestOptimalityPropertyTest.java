package app.meethalfway.meetings.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.meetings.domain.model.DomainConstants;
import app.meethalfway.meetings.domain.model.StrategyResult;
import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.shared.domain.ParticipantId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Properties for Fairest selection within its efficiency-feasible candidate set. */
class FairestOptimalityPropertyTest {

    private static final double EPSILON_MINUTES = 0.5;

    private final TieBreaker tieBreaker = new TieBreaker(EPSILON_MINUTES);
    private final FairestSelector selector = new FairestSelector(tieBreaker);

    @Provide
    Arbitrary<List<StrategyResult>> candidateLists() {
        Arbitrary<Double> sumTimes = Arbitraries.doubles().between(0.0, 5000.0);
        Arbitrary<Integer> maxTimes = Arbitraries.integers().between(0, 600);
        Arbitrary<Double> stdDevs = Arbitraries.doubles().between(0.0, 1000.0);
        Arbitrary<Integer> lats = Arbitraries.integers().between(-90, 90);
        Arbitrary<Integer> lngs = Arbitraries.integers().between(-180, 180);

        Arbitrary<StrategyResult> candidate = Combinators.combine(sumTimes, maxTimes, stdDevs, lats, lngs)
                .as((sum, max, std, lat, lng) -> {
                    Map<ParticipantId, Minutes> perParticipant = new LinkedHashMap<>();
                    perParticipant.put(new ParticipantId("p0"), new Minutes(max));
                    return new StrategyResult(new Coordinate(lat, lng), perParticipant, sum, max, std);
                });

        return candidate.list().ofMinSize(1).ofMaxSize(20);
    }

    @Property(tries = 200)
    void winnerIsFeasibleAndOptimalAtEveryOrderedTieBreakStage(
            @ForAll("candidateLists") List<StrategyResult> candidates) {
        Coordinate centroid = GeographicCentroid.of(candidates.stream().map(StrategyResult::point).toList());
        StrategyResult winner = selector.select(candidates, centroid);

        double minimumSum = candidates.stream().mapToDouble(StrategyResult::sumTime).min().orElseThrow();
        double threshold = (1.0 + DomainConstants.EFFICIENCY_TOLERANCE) * minimumSum + EPSILON_MINUTES;
        List<StrategyResult> feasible = candidates.stream()
                .filter(candidate -> candidate.sumTime() <= threshold)
                .toList();
        assertThat(feasible).contains(winner);

        List<StrategyResult> tied = new ArrayList<>(feasible);
        List<ToDoubleFunction<StrategyResult>> priorities =
                List.of(StrategyResult::stdDev, StrategyResult::sumTime, StrategyResult::maxTime);
        for (ToDoubleFunction<StrategyResult> metric : priorities) {
            double minimum = tied.stream().mapToDouble(metric).min().orElseThrow();
            assertThat(tieBreaker.areEqual(metric.applyAsDouble(winner), minimum))
                    .as("winner remains within epsilon of the minimum at each priority")
                    .isTrue();
            tied.removeIf(candidate -> !tieBreaker.areEqual(metric.applyAsDouble(candidate), minimum));
        }

        Comparator<StrategyResult> spatialOrder = Comparator
                .comparingDouble((StrategyResult candidate) ->
                        GeographicCentroid.squaredDistance(candidate.point(), centroid))
                .thenComparingDouble(candidate -> candidate.point().lat())
                .thenComparingDouble(candidate -> candidate.point().lng());
        assertThat(winner).isEqualTo(tied.stream().min(spatialOrder).orElseThrow());
    }
}
