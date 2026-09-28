package app.meethalfway.meetings.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import app.meethalfway.shared.domain.Coordinate;

/** Construction-time invariants and containment for {@link ServiceBounds} (Requirement 1.7). */
class ServiceBoundsTest {

    @Test
    void acceptsAValidBox() {
        ServiceBounds bounds = new ServiceBounds(6.0, 6.5, -75.7, -75.4);
        assertThat(bounds.minLat()).isEqualTo(6.0);
        assertThat(bounds.maxLng()).isEqualTo(-75.4);
    }

    @Test
    void acceptsADegenerateSinglePointBox() {
        assertThat(new ServiceBounds(6.0, 6.0, -75.5, -75.5)).isNotNull();
    }

    @Test
    void rejectsInvertedLatitudeBounds() {
        assertThatThrownBy(() -> new ServiceBounds(6.5, 6.0, -75.7, -75.4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minLat must be <= maxLat");
    }

    @Test
    void rejectsInvertedLongitudeBounds() {
        assertThatThrownBy(() -> new ServiceBounds(6.0, 6.5, -75.4, -75.7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minLng must be <= maxLng");
    }

    @Test
    void rejectsOutOfRangeLatitude() {
        assertThatThrownBy(() -> new ServiceBounds(-91.0, 6.5, -75.7, -75.4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minLat");
    }

    @Test
    void rejectsNonFiniteBound() {
        assertThatThrownBy(() -> new ServiceBounds(6.0, Double.NaN, -75.7, -75.4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finite");
    }

    @Test
    void containsReturnsTrueForAnInteriorCoordinate() {
        ServiceBounds bounds = new ServiceBounds(6.0, 6.5, -75.7, -75.4);
        assertThat(bounds.contains(new Coordinate(6.2, -75.5))).isTrue();
    }

    @Test
    void containsReturnsTrueOnTheEdge() {
        ServiceBounds bounds = new ServiceBounds(6.0, 6.5, -75.7, -75.4);
        assertThat(bounds.contains(new Coordinate(6.0, -75.7))).isTrue();
    }

    @Test
    void containsReturnsFalseOutsideTheBox() {
        ServiceBounds bounds = new ServiceBounds(6.0, 6.5, -75.7, -75.4);
        assertThat(bounds.contains(new Coordinate(7.0, -75.5))).isFalse();
    }

    @Test
    void containsRejectsNull() {
        ServiceBounds bounds = new ServiceBounds(6.0, 6.5, -75.7, -75.4);
        assertThatThrownBy(() -> bounds.contains(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }
}
