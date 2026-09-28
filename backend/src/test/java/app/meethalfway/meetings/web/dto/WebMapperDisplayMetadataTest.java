package app.meethalfway.meetings.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.meetings.domain.model.OutlierTradeoff;
import app.meethalfway.meetings.domain.model.RecommendationOutcome;
import app.meethalfway.meetings.domain.model.StrategyResult;
import app.meethalfway.meetings.domain.model.StrategyResults;
import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.shared.domain.ParticipantId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the additive display metadata {@link WebMapper} stamps onto
 * strategy results (Task 7.2): the stable {@code candidateId} strategy key, the
 * single {@code recommended} point per strategy, the backward-compatible
 * (unchanged) existing fields, and the empty {@code warnings} list for the valid
 * in-range case.
 *
 * <p>These are plain JUnit unit tests, not property tests (Properties 6–8 are
 * covered separately). All fixtures are private helpers local to this class so it
 * does not collide with sibling test classes exercising the same mapper.
 *
 * <p>Verifies Requirements 10.2 ({@code candidateId} = strategy key), 10.3 (each
 * candidate carries its full metric set + per-participant times), 10.4 (exactly
 * one {@code recommended} point per strategy), and 7.8 (a successful, in-range
 * response carries no warnings).
 */
class WebMapperDisplayMetadataTest {

    private final WebMapper mapper = new WebMapper();

    /** Builds an in-range strategy result with a distinct point and two participants. */
    private static StrategyResult inRangeResult(double lat, double lng, int minutes) {
        Map<ParticipantId, Minutes> perParticipant = new LinkedHashMap<>();
        perParticipant.put(new ParticipantId("p1"), new Minutes(minutes));
        perParticipant.put(new ParticipantId("p2"), new Minutes(minutes + 2));
        return new StrategyResult(
                new Coordinate(lat, lng),
                perParticipant,
                (minutes * 2) + 2, minutes + 2, 1.5);
    }

    /** Three distinct in-range strategy results so each strategy slot is populated. */
    private static StrategyResults sampleResults() {
        return new StrategyResults(
                inRangeResult(6.24, -75.57, 10),
                inRangeResult(6.25, -75.58, 11),
                inRangeResult(6.26, -75.59, 9));
    }

    private RecommendationResponse map(StrategyResults results) {
        return mapper.toRecommendationResponse(RecommendationOutcome.Success.of(results));
    }

    @Test
    void stampsCandidateIdEqualToStrategyKey() {
        StrategyResultsResponse results = map(sampleResults()).results();

        assertThat(results.fastest().candidateId()).isEqualTo("fastest");
        assertThat(results.minimax().candidateId()).isEqualTo("minimax");
        assertThat(results.fairest().candidateId()).isEqualTo("fairest");
    }

    @Test
    void marksExactlyOneRecommendedPointPerStrategy() {
        StrategyResultsResponse results = map(sampleResults()).results();

        // Each strategy returns a single selected point, which is its recommended one.
        assertThat(results.fastest().recommended()).isTrue();
        assertThat(results.minimax().recommended()).isTrue();
        assertThat(results.fairest().recommended()).isTrue();

        long recommendedCount = List.of(
                        results.fastest().recommended(),
                        results.minimax().recommended(),
                        results.fairest().recommended())
                .stream()
                .filter(Boolean::booleanValue)
                .count();
        assertThat(recommendedCount).isEqualTo(3);
    }

    @Test
    void preservesExistingStrategyResultFields() {
        StrategyResult fastest = inRangeResult(6.24, -75.57, 10);
        StrategyResults results = new StrategyResults(
                fastest,
                inRangeResult(6.25, -75.58, 11),
                inRangeResult(6.26, -75.59, 9));

        StrategyResultResponse mapped = map(results).results().fastest();

        // Additive metadata must not alter the existing coordinate/metric fields.
        assertThat(mapped.point().lat()).isEqualTo(6.24);
        assertThat(mapped.point().lng()).isEqualTo(-75.57);
        assertThat(mapped.sumTime()).isEqualTo(fastest.sumTime());
        assertThat(mapped.maxTime()).isEqualTo(fastest.maxTime());
        assertThat(mapped.stdDev()).isEqualTo(fastest.stdDev());
        assertThat(mapped.perParticipant())
                .containsEntry("p1", 10)
                .containsEntry("p2", 12)
                .hasSize(2);
    }

    @Test
    void reportsNoWarningsForFullyInRangeSuccess() {
        RecommendationResponse response = map(sampleResults());

        // Valid, in-range case: warnings present but empty, never null.
        assertThat(response.warnings()).isNotNull().isEmpty();
    }

    @Test
    void stampsMetadataOnOutlierTradeoffNestedResultsToo() {
        StrategyResults main = sampleResults();
        Set<ParticipantId> outliers = new LinkedHashSet<>();
        outliers.add(new ParticipantId("p2"));
        OutlierTradeoff tradeoff = new OutlierTradeoff(outliers, main, sampleResults(), 12.0, 8.0);

        RecommendationResponse response =
                mapper.toRecommendationResponse(RecommendationOutcome.Success.of(main, tradeoff));

        OutlierTradeoffResponse mapped = response.outlierTradeoff();
        assertThat(mapped).isNotNull();
        assertThat(mapped.including().fastest().candidateId()).isEqualTo("fastest");
        assertThat(mapped.including().minimax().candidateId()).isEqualTo("minimax");
        assertThat(mapped.including().fairest().candidateId()).isEqualTo("fairest");
        assertThat(mapped.excluding().fastest().recommended()).isTrue();
        assertThat(response.warnings()).isNotNull().isEmpty();
    }
}
