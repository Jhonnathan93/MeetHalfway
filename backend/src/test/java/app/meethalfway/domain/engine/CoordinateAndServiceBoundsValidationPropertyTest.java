package app.meethalfway.domain.engine;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import app.meethalfway.domain.engine.MeetingValidator.OutOfServiceBoundsException;
import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.EngineConfig;
import app.meethalfway.domain.model.MeetingInput;
import app.meethalfway.domain.model.OutlierRule;
import app.meethalfway.domain.model.ParticipantId;
import app.meethalfway.domain.model.ParticipantInput;
import app.meethalfway.domain.model.ServiceBounds;
import app.meethalfway.domain.model.TransportMode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.Tuple.Tuple2;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based test for the coordinate and service-bounds validation invariant
 * of {@link MeetingValidator}.
 *
 * <p>Feature: meeting-recommendation-engine, Property 3: Coordinate and
 * service-bounds validation invariant &mdash; the engine accepts a meeting only
 * if every participant location has a latitude in {@code [-90, 90]}, a longitude
 * in {@code [-180, 180]}, and falls within the configured service bounds;
 * otherwise the meeting is rejected with an error identifying the offending
 * participant.
 *
 * <p>The two validation layers live in different places by design, so this test
 * exercises both:
 * <ul>
 *   <li>Coordinate range (Requirement 1.6) is enforced at {@link Coordinate}
 *       construction, so an out-of-range location can never be built into a
 *       participant/meeting in the first place.</li>
 *   <li>Service bounds (Requirement 1.7) are config-driven and checked by
 *       {@link MeetingValidator} against {@link EngineConfig#serviceBounds()}.</li>
 * </ul>
 *
 * <p>Validates: Requirements 1.6, 1.7
 */
class CoordinateAndServiceBoundsValidationPropertyTest {

    /** A fixed, city-agnostic service box used for the service-bounds properties. */
    private static final ServiceBounds BOUNDS = new ServiceBounds(6.0, 6.5, -75.7, -75.4);

    private static final MeetingValidator VALIDATOR = new MeetingValidator();

    private static EngineConfig configWithBounds(ServiceBounds bounds) {
        return new EngineConfig(bounds, 25, 5_000.0, new OutlierRule.MedianMultiple(2.0), 0.5);
    }

    private static ParticipantInput participant(int index, Coordinate location) {
        return new ParticipantInput(new ParticipantId("p" + index), "Participant " + index, location);
    }

    // ---------------------------------------------------------------------
    // Generators
    // ---------------------------------------------------------------------

    /** Number of decimal places jqwik may use when generating coordinates. */
    private static final int COORD_SCALE = 6;

    /** A coordinate strictly inside {@link #BOUNDS}. */
    private static Arbitrary<Coordinate> inBoundsCoordinate() {
        Arbitrary<Double> lat =
                Arbitraries.doubles().between(BOUNDS.minLat(), BOUNDS.maxLat()).ofScale(COORD_SCALE);
        Arbitrary<Double> lng =
                Arbitraries.doubles().between(BOUNDS.minLng(), BOUNDS.maxLng()).ofScale(COORD_SCALE);
        return Combinators.combine(lat, lng).as(Coordinate::new);
    }

    /**
     * A range-valid coordinate that is guaranteed to fall OUTSIDE {@link #BOUNDS}
     * on at least one axis, while still being a constructible {@link Coordinate}
     * (latitude in [-90, 90], longitude in [-180, 180]).
     */
    private static Arbitrary<Coordinate> outOfBoundsCoordinate() {
        // Gaps use whole-degree offsets so the generator bounds themselves stay
        // exactly representable and never exceed the requested decimal scale.
        Arbitrary<Double> anyLat = Arbitraries.doubles().between(-90.0, 90.0).ofScale(COORD_SCALE);
        Arbitrary<Double> anyLng = Arbitraries.doubles().between(-180.0, 180.0).ofScale(COORD_SCALE);
        // Latitude clearly north of the box; longitude anywhere valid.
        Arbitrary<Coordinate> northOfBox = Combinators.combine(
                        Arbitraries.doubles().between(BOUNDS.maxLat() + 1.0, 90.0).ofScale(COORD_SCALE),
                        anyLng)
                .as(Coordinate::new);
        // Longitude clearly west of the box; latitude anywhere valid.
        Arbitrary<Coordinate> westOfBox = Combinators.combine(
                        anyLat,
                        Arbitraries.doubles().between(-180.0, BOUNDS.minLng() - 1.0).ofScale(COORD_SCALE))
                .as(Coordinate::new);
        // Longitude clearly east of the box; latitude anywhere valid.
        Arbitrary<Coordinate> eastOfBox = Combinators.combine(
                        anyLat,
                        Arbitraries.doubles().between(BOUNDS.maxLng() + 1.0, 180.0).ofScale(COORD_SCALE))
                .as(Coordinate::new);
        // Latitude clearly south of the box; longitude anywhere valid.
        Arbitrary<Coordinate> southOfBox = Combinators.combine(
                        Arbitraries.doubles().between(-90.0, BOUNDS.minLat() - 1.0).ofScale(COORD_SCALE),
                        anyLng)
                .as(Coordinate::new);
        return Arbitraries.oneOf(northOfBox, westOfBox, eastOfBox, southOfBox);
    }

    /** A meeting in which every participant location lies inside the service bounds. */
    @Provide
    Arbitrary<MeetingInput> meetingsFullyInBounds() {
        Arbitrary<Integer> count = Arbitraries.integers()
                .between(MeetingInput.MIN_PARTICIPANTS, MeetingInput.MAX_PARTICIPANTS);
        return count.flatMap(n -> inBoundsCoordinate().list().ofSize(n).map(locations -> {
            List<ParticipantInput> participants = new ArrayList<>();
            for (int i = 0; i < locations.size(); i++) {
                participants.add(participant(i, locations.get(i)));
            }
            return new MeetingInput(participants, TransportMode.DRIVING);
        }));
    }

    /**
     * A meeting with at least one out-of-bounds participant, paired with the
     * index of the first (list-order) offending participant so the test can
     * assert the error identifies exactly that participant.
     */
    @Provide
    Arbitrary<Tuple2<MeetingInput, Integer>> meetingsWithAtLeastOneOutOfBounds() {
        Arbitrary<Integer> countArb = Arbitraries.integers()
                .between(MeetingInput.MIN_PARTICIPANTS, MeetingInput.MAX_PARTICIPANTS);
        return countArb.flatMap(n -> Arbitraries.integers().between(0, n - 1).flatMap(offenderIndex ->
                // one out-of-bounds location for the offender slot ...
                outOfBoundsCoordinate().flatMap(outLocation ->
                        // ... and n-1 arbitrary (in- or out-of-bounds) locations for the rest,
                        // biased in-bounds so the chosen offender is the first out-of-bounds one.
                        inBoundsCoordinate().list().ofSize(n).map(fill -> {
                            List<ParticipantInput> participants = new ArrayList<>();
                            for (int i = 0; i < n; i++) {
                                Coordinate loc = i == offenderIndex ? outLocation : fill.get(i);
                                participants.add(participant(i, loc));
                            }
                            MeetingInput meeting = new MeetingInput(participants, TransportMode.WALKING);
                            return Tuple.of(meeting, offenderIndex);
                        }))));
    }

    // ---------------------------------------------------------------------
    // Property 3 — acceptance side (Requirement 1.7, in-bounds)
    // ---------------------------------------------------------------------

    /**
     * When every participant location is range-valid and within the configured
     * service bounds, the validator accepts the meeting (no exception thrown).
     */
    @Property(tries = 100)
    void acceptsMeetingWhenEveryLocationIsWithinServiceBounds(
            @ForAll("meetingsFullyInBounds") MeetingInput meeting) {
        EngineConfig config = configWithBounds(BOUNDS);

        VALIDATOR.validate(meeting, config);

        // Sanity: every location is indeed inside the bounds.
        assertThat(meeting.participants())
                .allSatisfy(p -> assertThat(BOUNDS.contains(p.location())).isTrue());
    }

    // ---------------------------------------------------------------------
    // Property 3 — rejection side (Requirement 1.7, out-of-bounds)
    // ---------------------------------------------------------------------

    /**
     * When at least one participant location falls outside the service bounds,
     * the validator rejects the meeting and the error identifies the first
     * offending participant.
     */
    @Property(tries = 100)
    void rejectsMeetingIdentifyingTheFirstOutOfBoundsParticipant(
            @ForAll("meetingsWithAtLeastOneOutOfBounds") Tuple2<MeetingInput, Integer> testCase) {
        MeetingInput meeting = testCase.get1();
        int expectedOffenderIndex = testCase.get2();
        ParticipantInput expectedOffender = meeting.participants().get(expectedOffenderIndex);
        EngineConfig config = configWithBounds(BOUNDS);

        OutOfServiceBoundsException error = catchThrowableOfType(
                () -> VALIDATOR.validate(meeting, config), OutOfServiceBoundsException.class);

        assertThat(error).isNotNull();
        assertThat(error.offendingParticipant()).isEqualTo(expectedOffender);
        assertThat(error.getMessage())
                .contains(expectedOffender.id().value())
                .contains("service bounds");
    }

    // ---------------------------------------------------------------------
    // Property 3 — coordinate range (Requirement 1.6)
    // ---------------------------------------------------------------------

    /**
     * Any out-of-range latitude or longitude is rejected at {@link Coordinate}
     * construction, so such a location can never be built into a meeting. This is
     * the coordinate-range half of the invariant (Requirement 1.6).
     */
    @Property(tries = 100)
    void rejectsOutOfRangeCoordinatesAtConstruction(
            @ForAll("outOfRangeLatLng") Tuple2<Double, Double> latLng) {
        double lat = latLng.get1();
        double lng = latLng.get2();

        assertThatThrownBy(() -> new Coordinate(lat, lng))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Latitude/longitude pairs with at least one component outside the universal
     * valid range ([-90, 90] latitude, [-180, 180] longitude).
     */
    @Provide
    Arbitrary<Tuple2<Double, Double>> outOfRangeLatLng() {
        Arbitrary<Double> outLat = Arbitraries.oneOf(
                Arbitraries.doubles().between(-1_000.0, -90.0001).ofScale(COORD_SCALE),
                Arbitraries.doubles().between(90.0001, 1_000.0).ofScale(COORD_SCALE));
        Arbitrary<Double> outLng = Arbitraries.oneOf(
                Arbitraries.doubles().between(-10_000.0, -180.0001).ofScale(COORD_SCALE),
                Arbitraries.doubles().between(180.0001, 10_000.0).ofScale(COORD_SCALE));
        Arbitrary<Double> anyLat = Arbitraries.doubles().between(-90.0, 90.0).ofScale(COORD_SCALE);
        Arbitrary<Double> anyLng = Arbitraries.doubles().between(-180.0, 180.0).ofScale(COORD_SCALE);

        // At least one axis is out of range: bad-lat/any-lng, any-lat/bad-lng, bad/bad.
        Arbitrary<Tuple2<Double, Double>> badLat =
                Combinators.combine(outLat, anyLng).as(Tuple::of);
        Arbitrary<Tuple2<Double, Double>> badLng =
                Combinators.combine(anyLat, outLng).as(Tuple::of);
        Arbitrary<Tuple2<Double, Double>> bothBad =
                Combinators.combine(outLat, outLng).as(Tuple::of);
        return Arbitraries.oneOf(badLat, badLng, bothBad);
    }

    // ---------------------------------------------------------------------
    // Property 3 — city-agnosticism: bounds come from EngineConfig, not code
    // ---------------------------------------------------------------------

    /**
     * The validator's decision follows the configured bounds, not any hard-coded
     * region: for an arbitrary in-range coordinate and an arbitrary valid service
     * box, acceptance holds exactly when {@link ServiceBounds#contains(Coordinate)}
     * is true. This keeps the optimization logic city-agnostic (Requirements 5.4,
     * 11.5).
     */
    @Property(tries = 100)
    void decisionMatchesConfiguredBoundsForArbitraryBoxAndPoint(
            @ForAll("validBoxes") ServiceBounds bounds,
            @ForAll @IntRange(min = -90, max = 90) int lat,
            @ForAll @IntRange(min = -180, max = 180) int lng) {
        Coordinate point = new Coordinate(lat, lng);
        // Two participants (minimum valid meeting), both at the same point.
        List<ParticipantInput> people = new ArrayList<>();
        people.add(participant(0, point));
        people.add(participant(1, point));
        MeetingInput meeting = new MeetingInput(people, TransportMode.DRIVING);
        EngineConfig config = configWithBounds(bounds);

        boolean accepted;
        try {
            VALIDATOR.validate(meeting, config);
            accepted = true;
        } catch (OutOfServiceBoundsException e) {
            accepted = false;
        }

        assertThat(accepted).isEqualTo(bounds.contains(point));
    }

    /** Arbitrary, well-formed service boxes (min &le; max on both axes). */
    @Provide
    Arbitrary<ServiceBounds> validBoxes() {
        Arbitrary<List<Integer>> lats = Arbitraries.integers().between(-90, 90).list().ofSize(2);
        Arbitrary<List<Integer>> lngs = Arbitraries.integers().between(-180, 180).list().ofSize(2);
        return Combinators.combine(lats, lngs).as((la, ln) ->
                new ServiceBounds(
                        Math.min(la.get(0), la.get(1)),
                        Math.max(la.get(0), la.get(1)),
                        Math.min(ln.get(0), ln.get(1)),
                        Math.max(ln.get(0), ln.get(1))));
    }
}
