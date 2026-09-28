package app.meethalfway.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Construction-time invariant checks for {@link Coordinate} (Requirement 1.6). */
class CoordinateTest {

    @Test
    void acceptsCoordinateWithinValidRange() {
        Coordinate coordinate = new Coordinate(6.25, -75.56);

        assertThat(coordinate.lat()).isEqualTo(6.25);
        assertThat(coordinate.lng()).isEqualTo(-75.56);
    }

    @Test
    void acceptsBoundaryValues() {
        assertThat(new Coordinate(-90.0, -180.0)).isNotNull();
        assertThat(new Coordinate(90.0, 180.0)).isNotNull();
    }

    @Test
    void rejectsLatitudeBelowMinimum() {
        assertThatThrownBy(() -> new Coordinate(-90.0001, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latitude");
    }

    @Test
    void rejectsLatitudeAboveMaximum() {
        assertThatThrownBy(() -> new Coordinate(90.0001, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latitude");
    }

    @Test
    void rejectsLongitudeBelowMinimum() {
        assertThatThrownBy(() -> new Coordinate(0.0, -180.0001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longitude");
    }

    @Test
    void rejectsLongitudeAboveMaximum() {
        assertThatThrownBy(() -> new Coordinate(0.0, 180.0001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longitude");
    }

    @Test
    void rejectsNonFiniteValues() {
        assertThatThrownBy(() -> new Coordinate(Double.NaN, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coordinate(0.0, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
