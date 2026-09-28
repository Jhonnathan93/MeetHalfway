package app.meethalfway.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Construction-time invariant checks for {@link Minutes} (Requirement 8.2). */
class MinutesTest {

    @Test
    void acceptsZero() {
        assertThat(new Minutes(0).value()).isZero();
    }

    @Test
    void acceptsPositiveValue() {
        assertThat(new Minutes(42).value()).isEqualTo(42);
    }

    @Test
    void rejectsNegativeValue() {
        assertThatThrownBy(() -> new Minutes(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }
}
