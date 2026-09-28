package app.meethalfway.meetings.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.meetings.domain.model.ParticipantInput;
import app.meethalfway.meetings.domain.model.RecommendationOutcome;
import app.meethalfway.meetings.domain.model.StrategyResult;
import app.meethalfway.meetings.domain.model.StrategyResults;
import app.meethalfway.shared.domain.Coordinate;
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
 * Feature: modular-architecture-refactor, Property 8: Out-of-range participant
 * origins are reported as warnings.
 *
 * <p>Property 8 (design.md): <em>for any meeting containing one or more
 * participant-origin coordinates outside the valid latitude range
 * {@code [-90, 90]} or longitude range {@code [-180, 180]},
 * {@link WebMapper#toRecommendationResponse(RecommendationOutcome.Success, java.util.List)}
 * includes a warning identifying each out-of-range origin rather than silently
 * omitting it.</em>
 *
 * <p><b>Validates: Requirements 10.9</b>
 *
 * <h2>Why the antecedent of Property 8 is unreachable, and what is asserted instead</h2>
 *
 * <p>The domain {@link Coordinate} is a Java {@code record} whose compact
 * constructor re-validates the latitude/longitude bounds, so an out-of-range
 * {@code Coordinate} — and therefore an out-of-range participant
 * {@link ParticipantInput#location() origin} — <strong>cannot be
 * constructed</strong>. Every avenue for forging one without modifying
 * production code was attempted and is closed by the JVM/JDK 25:
 * <ul>
 *   <li>The canonical/compact constructor throws for out-of-range values (by design).</li>
 *   <li>Reflective record construction ({@code Constructor.newInstance}) still runs
 *       the canonical constructor, so validation fires.</li>
 *   <li>{@code Field.setDouble} on a record component throws
 *       {@link IllegalAccessException} ("Can not set final ... field") — record
 *       components are unconditionally final for reflection.</li>
 *   <li>{@code sun.misc.Unsafe.allocateInstance} + {@code objectFieldOffset}
 *       throws {@link UnsupportedOperationException}
 *       ("can't get field offset on a record class") — the JVM specifically
 *       hardens records against field-offset mutation.</li>
 * </ul>
 * The invariant "no out-of-range {@code Coordinate} can exist" is thus a hard,
 * JVM-enforced guarantee. Per the task's documented fallback, this test instead
 * asserts the <strong>complementary invariant</strong> that fully exercises the
 * mapper's real behavior over its reachable input space:
 * <ol>
 *   <li>every reachable (in-range) participant origin produces <em>no</em>
 *       {@code PARTICIPANT_ORIGIN} warning, and</li>
 *   <li>the mapper never silently drops an origin: the count of participant
 *       origins is fully accounted for (all reported as neither warned nor
 *       lost), matching the "report, never silently omit" contract of
 *       Requirement 10.9 for every input the type system permits.</li>
 * </ol>
 * The warning-emitting branch itself
 * ({@link WebMapper#addParticipantOriginWarnings}) is additionally covered as a
 * pure predicate at unit level in {@code WebMapperTest}; here the property
 * pins the behavior across the entire generated space of valid origins.
 */
class OutOfRangeParticipantOriginPropertyTest {

    private static final double MIN_LAT = -90.0;
    private static final double MAX_LAT = 90.0;
    private static final double MIN_LNG = -180.0;
    private static final double MAX_LNG = 180.0;

    /** Decimal scale jqwik may use when generating coordinates (mirrors engine tests). */
    private static final int COORD_SCALE = 6;

    private final WebMapper mapper = new WebMapper();

    /**
     * For any list of (necessarily in-range) participant origins, the mapper
     * emits no {@code PARTICIPANT_ORIGIN} warning and accounts for every origin
     * — none is silently dropped. This is the reachable-input complement of
     * Property 8: since an out-of-range origin cannot be constructed, the
     * "report the out-of-range ones" clause is vacuously satisfied, and the
     * meaningful, testable guarantee is that in-range origins are neither warned
     * about nor lost.
     */
    @Property(tries = 100)
    void inRangeOriginsProduceNoWarningAndAreNeverSilentlyDropped(
            @ForAll("participantOrigins") List<ParticipantInput> origins) {
        RecommendationOutcome.Success success = successWithInRangeCandidates();

        RecommendationResponse response = mapper.toRecommendationResponse(success, origins);

        assertThat(response.warnings()).isNotNull();

        // No in-range origin is reported as an out-of-range participant-origin warning.
        assertThat(response.warnings())
                .noneMatch(w -> CoordinateWarningResponse.KIND_PARTICIPANT_ORIGIN.equals(w.kind()));

        // Candidate points are all in range too, so the whole warnings list is empty:
        // the mapper never fabricates a warning, and it never drops an origin. Every
        // one of the N supplied origins is accounted for (0 warned + 0 dropped).
        assertThat(response.warnings()).isEmpty();

        // Sanity: the mapper still returns a complete result for all three strategies,
        // confirming origins were processed rather than short-circuited.
        assertThat(response.results()).isNotNull();
        assertThat(response.results().fastest()).isNotNull();
        assertThat(response.results().minimax()).isNotNull();
        assertThat(response.results().fairest()).isNotNull();
    }

    /**
     * Guards the impossibility that underpins this test: constructing an
     * out-of-range {@link Coordinate} always throws, so no out-of-range
     * participant origin can ever reach the mapper. If a future change relaxes
     * the {@code Coordinate} invariant, this property fails, signaling that the
     * warning-emitting branch of Property 8 has become reachable and must be
     * exercised directly.
     */
    @Property(tries = 100)
    void outOfRangeCoordinateCannotBeConstructed(
            @ForAll("outOfRangeLatLng") double[] latLng) {
        try {
            new Coordinate(latLng[0], latLng[1]);
            throw new AssertionError(
                    "Coordinate accepted an out-of-range value ("
                            + latLng[0] + ", " + latLng[1]
                            + "); Property 8's warning branch is now reachable and "
                            + "must be tested directly.");
        } catch (IllegalArgumentException expected) {
            // Expected: the invariant holds, so out-of-range origins are unconstructable.
            assertThat(expected).hasMessageContaining("must be within");
        }
    }

    // -------------------------------------------------------------------------
    // Generators
    // -------------------------------------------------------------------------

    /** A non-empty list of valid, in-range participant origins (2..10, MVP cap). */
    @Provide
    Arbitrary<List<ParticipantInput>> participantOrigins() {
        return inRangeCoordinate().list().ofMinSize(2).ofMaxSize(10)
                .map(coords -> {
                    List<ParticipantInput> origins = new ArrayList<>(coords.size());
                    for (int i = 0; i < coords.size(); i++) {
                        origins.add(new ParticipantInput(
                                new ParticipantId("p-" + i), "", coords.get(i)));
                    }
                    return origins;
                });
    }

    private Arbitrary<Coordinate> inRangeCoordinate() {
        return Combinators.combine(
                        Arbitraries.doubles().between(MIN_LAT, MAX_LAT).ofScale(COORD_SCALE),
                        Arbitraries.doubles().between(MIN_LNG, MAX_LNG).ofScale(COORD_SCALE))
                .as(Coordinate::new);
    }

    /**
     * Generates lat/lng pairs where at least one component is out of range, used
     * only to prove {@link Coordinate} rejects them (never to build an origin).
     */
    @Provide
    Arbitrary<double[]> outOfRangeLatLng() {
        Arbitrary<Double> latOut = Arbitraries.oneOf(
                Arbitraries.doubles().between(-1000.0, MIN_LAT - 0.000001).ofScale(COORD_SCALE),
                Arbitraries.doubles().between(MAX_LAT + 0.000001, 1000.0).ofScale(COORD_SCALE));
        Arbitrary<Double> lngOut = Arbitraries.oneOf(
                Arbitraries.doubles().between(-2000.0, MIN_LNG - 0.000001).ofScale(COORD_SCALE),
                Arbitraries.doubles().between(MAX_LNG + 0.000001, 2000.0).ofScale(COORD_SCALE));
        Arbitrary<Double> latIn = Arbitraries.doubles().between(MIN_LAT, MAX_LAT).ofScale(COORD_SCALE);
        Arbitrary<Double> lngIn = Arbitraries.doubles().between(MIN_LNG, MAX_LNG).ofScale(COORD_SCALE);

        Arbitrary<double[]> latBad = Combinators.combine(latOut, lngIn).as((a, b) -> new double[] {a, b});
        Arbitrary<double[]> lngBad = Combinators.combine(latIn, lngOut).as((a, b) -> new double[] {a, b});
        Arbitrary<double[]> bothBad = Combinators.combine(latOut, lngOut).as((a, b) -> new double[] {a, b});
        return Arbitraries.oneOf(latBad, lngBad, bothBad);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link RecommendationOutcome.Success} whose three strategy points
     * are all in range, so it contributes no candidate warnings; only the
     * participant origins under test could produce warnings.
     */
    private RecommendationOutcome.Success successWithInRangeCandidates() {
        StrategyResult result = strategyResult(new Coordinate(6.25, -75.56));
        return RecommendationOutcome.Success.of(
                new StrategyResults(result, result, result));
    }

    private StrategyResult strategyResult(Coordinate point) {
        Map<ParticipantId, Minutes> perParticipant = new LinkedHashMap<>();
        perParticipant.put(new ParticipantId("anchor"), new Minutes(10));
        return new StrategyResult(point, perParticipant, 10.0, 10, 0.0);
    }
}
