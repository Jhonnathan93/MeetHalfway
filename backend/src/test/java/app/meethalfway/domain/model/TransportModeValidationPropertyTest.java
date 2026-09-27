package app.meethalfway.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

/**
 * Feature: meeting-recommendation-engine, Property 2: Transport-mode validation invariant
 *
 * <p>For any submitted transport-mode value, the engine accepts the meeting only
 * when the mode is exactly "driving" or "walking" (mapping to
 * {@link TransportMode#DRIVING} / {@link TransportMode#WALKING}); any other value
 * is rejected with a validation error naming the supported modes.
 *
 * <p><b>Validates: Requirements 1.4, 1.5</b>
 */
class TransportModeValidationPropertyTest {

    @Property(tries = 100)
    void transportModeValueIsAcceptedIffExactlyDrivingOrWalking(
            @ForAll("candidateModeValues") String value) {
        boolean isDriving = TransportMode.DRIVING_VALUE.equals(value);
        boolean isWalking = TransportMode.WALKING_VALUE.equals(value);

        if (isDriving) {
            // Exactly "driving" is accepted and maps to DRIVING.
            assertThat(TransportMode.fromValue(value)).isEqualTo(TransportMode.DRIVING);
        } else if (isWalking) {
            // Exactly "walking" is accepted and maps to WALKING.
            assertThat(TransportMode.fromValue(value)).isEqualTo(TransportMode.WALKING);
        } else {
            // Any other value is rejected with an error naming both supported modes.
            assertThatThrownBy(() -> TransportMode.fromValue(value))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(TransportMode.DRIVING_VALUE)
                    .hasMessageContaining(TransportMode.WALKING_VALUE);
        }
    }

    /**
     * Generates a wide space of candidate transport-mode strings: the two exact
     * supported values, deliberate near-misses (case, whitespace, aliases), and
     * arbitrary strings. This drives every iteration through the accept/reject
     * boundary of {@link TransportMode#fromValue(String)}.
     */
    @Provide
    Arbitrary<String> candidateModeValues() {
        Arbitrary<String> supported =
                Arbitraries.of(TransportMode.DRIVING_VALUE, TransportMode.WALKING_VALUE);

        Arbitrary<String> nearMisses = Arbitraries.of(
                "",
                " ",
                "Driving",
                "DRIVING",
                "Walking",
                "WALKING",
                " driving",
                "driving ",
                "walking ",
                "drive",
                "walk",
                "car",
                "cycling",
                "transit",
                "flying");

        Arbitrary<String> arbitrary = Arbitraries.strings().ofMaxLength(20);

        // Weight toward the supported/near-miss boundary while still exercising
        // fully arbitrary noise, so both accepted and rejected branches recur.
        return Arbitraries.frequencyOf(
                Tuple.of(3, supported),
                Tuple.of(3, nearMisses),
                Tuple.of(4, arbitrary));
    }
}
