package app.meethalfway.meetings.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.meetings.domain.model.OutlierRule;
import app.meethalfway.shared.domain.ParticipantId;
import app.meethalfway.meetings.domain.model.ServiceBounds;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Feature: meeting-recommendation-engine, Property 14: Outlier detection matches
 * the configured rule
 *
 * <p>For any vector of per-participant travel times, the set of participants
 * flagged by {@link ConfiguredOutlierDetector} exactly equals the configured
 * {@link OutlierRule} applied to that vector, using the strict boundary
 * {@code t_i > threshold} (a participant exactly on the threshold is not an
 * outlier).
 *
 * <p>Validates: Requirements 7.1.
 *
 * <p>The expected outlier set is computed independently in this test by
 * reproducing the exact median (average-of-two-middle) and percentile
 * (R-7 / Excel {@code PERCENTILE.INC} linear interpolation) formulas the
 * detector's contract specifies, so the property is not merely a re-run of the
 * production code.
 */
class OutlierDetectionPropertyTest {

    private final ConfiguredOutlierDetector detector = new ConfiguredOutlierDetector();

    /**
     * For any travel-time map (1-10 participants, non-negative whole minutes) and
     * any supported outlier rule, {@code detect(...)} returns exactly the set of
     * participants whose value strictly exceeds the independently-computed
     * threshold.
     */
    @Property(tries = 100)
    void detectMatchesTheConfiguredRule(@ForAll("scenarios") Scenario scenario) {
        Set<ParticipantId> actual = detector.detect(scenario.travelTimes(), scenario.config());

        Set<ParticipantId> expected = expectedOutliers(scenario.travelTimes(), scenario.config().outlierRule());

        assertThat(actual).isEqualTo(expected);
    }

    // ---- Independent reference implementation of the configured rule ----

    private static Set<ParticipantId> expectedOutliers(
            Map<ParticipantId, Minutes> travelTimes, OutlierRule rule) {
        if (travelTimes.isEmpty()) {
            return Set.of();
        }
        double[] sorted = travelTimes.values().stream()
                .mapToDouble(Minutes::value)
                .sorted()
                .toArray();
        double threshold = switch (rule) {
            case OutlierRule.MedianMultiple mm -> mm.k() * median(sorted);
            case OutlierRule.Percentile pct -> percentile(sorted, pct.p());
        };
        return travelTimes.entrySet().stream()
                .filter(e -> e.getValue().value() > threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static double median(double[] sorted) {
        int n = sorted.length;
        int mid = n / 2;
        if (n % 2 == 1) {
            return sorted[mid];
        }
        return (sorted[mid - 1] + sorted[mid]) / 2.0;
    }

    private static double percentile(double[] sorted, double p) {
        int n = sorted.length;
        double rank = (p / 100.0) * (n - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) {
            return sorted[lo];
        }
        return sorted[lo] + (rank - lo) * (sorted[hi] - sorted[lo]);
    }

    // ---- Generators ----

    @Provide
    Arbitrary<Scenario> scenarios() {
        Arbitrary<List<Integer>> times = Arbitraries.integers()
                .between(0, 240)
                .list()
                .ofMinSize(1)
                .ofMaxSize(10);

        Arbitrary<OutlierRule> medianRules = Arbitraries.doubles()
                .between(0.5, 5.0)
                .ofScale(2)
                .filter(k -> k > 0.0)
                .map(OutlierRule.MedianMultiple::new);

        // p in (0, 100]; scale 2 keeps generated values representable (min 0.01).
        Arbitrary<OutlierRule> percentileRules = Arbitraries.doubles()
                .between(0.01, 100.0)
                .ofScale(2)
                .map(OutlierRule.Percentile::new);

        Arbitrary<OutlierRule> rules = Arbitraries.oneOf(medianRules, percentileRules);

        return Combinators.combine(times, rules).as(Scenario::of);
    }

    private record Scenario(Map<ParticipantId, Minutes> travelTimes, EngineConfig config) {

        private static final ServiceBounds BOUNDS = new ServiceBounds(6.0, 6.5, -75.7, -75.4);

        static Scenario of(List<Integer> times, OutlierRule rule) {
            Map<ParticipantId, Minutes> travelTimes = new LinkedHashMap<>();
            List<Integer> values = new ArrayList<>(times);
            for (int i = 0; i < values.size(); i++) {
                travelTimes.put(new ParticipantId("p" + i), new Minutes(values.get(i)));
            }
            EngineConfig config = new EngineConfig(BOUNDS, 10, 15000.0, rule, 0.5);
            return new Scenario(travelTimes, config);
        }
    }
}
