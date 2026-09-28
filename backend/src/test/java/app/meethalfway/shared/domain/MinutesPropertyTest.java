package app.meethalfway.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for the {@link Minutes} travel-time value object.
 *
 * <p>Feature: meeting-recommendation-engine, Property 16: Travel-time units and
 * non-negativity
 *
 * <p>Validates: Requirements 8.2 — every computed/constructed travel time is a
 * whole (integer) number of minutes and is non-negative. {@link Minutes} holds
 * an {@code int} (guaranteeing whole minutes) and rejects negative values at
 * construction, so a travel time can never be fractional or negative.
 */
class MinutesPropertyTest {

    /**
     * For any non-negative integer, {@link Minutes} constructs successfully and
     * exposes a whole, non-negative value equal to the input.
     */
    @Property(tries = 100)
    void anyNonNegativeIntegerYieldsWholeNonNegativeMinutes(
            @ForAll @IntRange(min = 0, max = Integer.MAX_VALUE) int candidate) {
        Minutes minutes = new Minutes(candidate);

        assertThat(minutes.value())
                .isGreaterThanOrEqualTo(0)
                .isEqualTo(candidate);
    }

    /**
     * For any negative integer, {@link Minutes} rejects construction so a
     * negative travel time can never exist.
     */
    @Property(tries = 100)
    void anyNegativeIntegerIsRejected(
            @ForAll @IntRange(min = Integer.MIN_VALUE, max = -1) int negative) {
        assertThatThrownBy(() -> new Minutes(negative))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }
}
