package app.meethalfway.meetings.web.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.shared.domain.ParticipantId;
import app.meethalfway.meetings.domain.model.RecommendationOutcome;
import app.meethalfway.meetings.domain.model.StrategyResult;
import app.meethalfway.meetings.domain.model.StrategyResults;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Feature: modular-architecture-refactor, Property 7: Out-of-range candidate
 * points are excluded transparently.
 *
 * <p>Validates: Requirements 10.7.
 *
 * <p>Given candidate coordinates outside the valid latitude/longitude ranges,
 * {@link WebMapper#toRecommendationResponse(RecommendationOutcome.Success)}
 * must (a) exclude exactly those invalid candidates from {@code results}
 * (their strategy slot becomes {@code null}), (b) retain every valid candidate
 * unchanged, and (c) emit one {@code CANDIDATE} warning per excluded coordinate
 * that identifies it (by {@code candidateId} reference and its lat/lng) — never
 * silently omitting an invalid point and never rejecting the whole response.
 *
 * <p><strong>Test seam for out-of-range coordinates.</strong> The production
 * {@link Coordinate} record re-validates latitude {@code [-90, 90]} and
 * longitude {@code [-180, 180]} in its compact constructor (per task 7.1's
 * note), so an out-of-range {@code Coordinate} can never be built directly and
 * no production code may be changed to expose one. However {@link StrategyResult}
 * only null-checks its {@code point}; it does not range-check the coordinate.
 * That is the seam this test uses: a Mockito mock of {@code Coordinate} (mockable
 * as a record via Mockito 5's inline mock maker) returns out-of-range
 * {@code lat()}/{@code lng()} values and is wrapped in a real
 * {@code StrategyResult}. The mapper's own {@code isInRange} branch is thereby
 * exercised end to end through the real {@link WebMapper}, with no production
 * change. Valid candidates use genuine, in-range {@code Coordinate} instances.
 */
class OutOfRangeCandidateExclusionPropertyTest {

    private final WebMapper mapper = new WebMapper();

    /**
     * Property 7 — for every combination of in-range / out-of-range candidate
     * points across the three strategies, the mapper excludes exactly the
     * out-of-range slots (leaving them {@code null}), keeps every in-range slot
     * populated and faithful to its source, and produces exactly one
     * {@code CANDIDATE} warning per excluded point identifying it. The whole
     * response is never rejected: mapping always succeeds and {@code warnings}
     * is never {@code null}.
     */
    @Property(tries = 200)
    void outOfRangeCandidatesAreExcludedWithAWarningWhileValidOnesAreRetained(
            @ForAll("candidatePlans") List<CandidatePlan> plans) {

        StrategyResult fastest = plans.get(0).toStrategyResult();
        StrategyResult minimax = plans.get(1).toStrategyResult();
        StrategyResult fairest = plans.get(2).toStrategyResult();

        RecommendationOutcome.Success success = RecommendationOutcome.Success.of(
                new StrategyResults(fastest, minimax, fairest));

        // (c) The whole response is never rejected because a candidate is invalid.
        RecommendationResponse[] holder = new RecommendationResponse[1];
        assertThatCode(() -> holder[0] = mapper.toRecommendationResponse(success))
                .doesNotThrowAnyException();
        RecommendationResponse response = holder[0];

        assertThat(response).isNotNull();
        assertThat(response.warnings()).isNotNull();

        StrategyResultsResponse results = response.results();
        assertThat(results).isNotNull();

        // Pair each strategy slot with its plan and expected candidateId.
        record Slot(CandidatePlan plan, StrategyResultResponse mapped, String candidateId) {
        }
        List<Slot> slots = List.of(
                new Slot(plans.get(0), results.fastest(), "fastest"),
                new Slot(plans.get(1), results.minimax(), "minimax"),
                new Slot(plans.get(2), results.fairest(), "fairest"));

        int expectedWarnings = 0;
        for (Slot slot : slots) {
            if (slot.plan().inRange()) {
                // (b) Valid candidate retained, unchanged, and correctly stamped.
                assertThat(slot.mapped())
                        .as("in-range %s slot must be retained", slot.candidateId())
                        .isNotNull();
                assertThat(slot.mapped().candidateId()).isEqualTo(slot.candidateId());
                assertThat(slot.mapped().recommended()).isTrue();
                assertThat(slot.mapped().point().lat()).isEqualTo(slot.plan().lat());
                assertThat(slot.mapped().point().lng()).isEqualTo(slot.plan().lng());
            } else {
                // (a) Invalid candidate excluded: its slot is null.
                expectedWarnings++;
                assertThat(slot.mapped())
                        .as("out-of-range %s slot must be excluded (null)", slot.candidateId())
                        .isNull();

                // (c) Exactly one CANDIDATE warning identifies this excluded point.
                List<CoordinateWarningResponse> matching = response.warnings().stream()
                        .filter(w -> CoordinateWarningResponse.KIND_CANDIDATE.equals(w.kind()))
                        .filter(w -> slot.candidateId().equals(w.reference()))
                        .toList();
                assertThat(matching)
                        .as("exactly one CANDIDATE warning for %s", slot.candidateId())
                        .hasSize(1);
                CoordinateWarningResponse warning = matching.get(0);
                assertThat(warning.lat()).isEqualTo(slot.plan().lat());
                assertThat(warning.lng()).isEqualTo(slot.plan().lng());
                assertThat(warning.reason()).isNotBlank();
            }
        }

        // The warning count matches exactly the number of out-of-range candidates:
        // nothing is silently dropped and no spurious warning is added. No
        // participant origins are supplied, so all warnings are CANDIDATE warnings.
        assertThat(response.warnings()).hasSize(expectedWarnings);
        assertThat(response.warnings())
                .allMatch(w -> CoordinateWarningResponse.KIND_CANDIDATE.equals(w.kind()));
    }

    // ---- Generators ----

    /**
     * Plans for the three strategy slots (fastest, minimax, fairest), each
     * independently in-range or out-of-range, so every combination — including
     * all-valid, all-invalid, and mixed — is exercised.
     */
    @Provide
    Arbitrary<List<CandidatePlan>> candidatePlans() {
        return candidatePlan().list().ofSize(3);
    }

    private Arbitrary<CandidatePlan> candidatePlan() {
        Arbitrary<Boolean> inRange = Arbitraries.of(true, false);
        // In-range coordinates: strictly within the valid ranges.
        Arbitrary<Double> validLat = Arbitraries.doubles().between(-89.0, 89.0).ofScale(4);
        Arbitrary<Double> validLng = Arbitraries.doubles().between(-179.0, 179.0).ofScale(4);
        // Out-of-range coordinates: at least one axis outside its valid range.
        Arbitrary<Double> badLat = Arbitraries.doubles().between(90.001, 500.0).ofScale(4);
        Arbitrary<Double> badLng = Arbitraries.doubles().between(180.001, 500.0).ofScale(4);
        Arbitrary<Integer> minutes = Arbitraries.integers().between(1, 120);

        return Combinators.combine(inRange, validLat, validLng, badLat, badLng, minutes)
                .as((valid, vLat, vLng, bLat, bLng, m) ->
                        valid
                                ? new CandidatePlan(true, vLat, vLng, m)
                                : new CandidatePlan(false, bLat, bLng, m));
    }

    /**
     * A recipe for one strategy slot: whether its candidate coordinate is
     * in-range, the lat/lng to use, and the per-participant minutes. In-range
     * plans build a real {@link Coordinate}; out-of-range plans build a Mockito
     * mock returning the out-of-range values (the only way to smuggle an
     * out-of-range coordinate past {@code Coordinate}'s validating constructor).
     */
    private record CandidatePlan(boolean inRange, double lat, double lng, int minutes) {

        StrategyResult toStrategyResult() {
            Coordinate point = inRange ? new Coordinate(lat, lng) : outOfRangeCoordinate(lat, lng);
            Map<ParticipantId, Minutes> perParticipant = new LinkedHashMap<>();
            perParticipant.put(new ParticipantId("p1"), new Minutes(minutes));
            perParticipant.put(new ParticipantId("p2"), new Minutes(minutes + 1));
            int max = minutes + 1;
            double sum = (double) minutes + (minutes + 1);
            return new StrategyResult(point, perParticipant, sum, max, 0.5);
        }

        private static Coordinate outOfRangeCoordinate(double lat, double lng) {
            Coordinate mocked = mock(Coordinate.class);
            when(mocked.lat()).thenReturn(lat);
            when(mocked.lng()).thenReturn(lng);
            return mocked;
        }
    }
}
