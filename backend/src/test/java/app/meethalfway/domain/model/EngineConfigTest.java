package app.meethalfway.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Construction-time invariants for {@link EngineConfig} (Requirements 4.3, 5.4, 11.5). */
class EngineConfigTest {

    private static ServiceBounds bounds() {
        return new ServiceBounds(6.0, 6.5, -75.7, -75.4);
    }

    private static OutlierRule rule() {
        return new OutlierRule.MedianMultiple(2.0);
    }

    @Test
    void acceptsAValidConfig() {
        EngineConfig config = new EngineConfig(bounds(), 100, 15000.0, rule(), 0.5);
        assertThat(config.gridDensityN()).isEqualTo(100);
        assertThat(config.maxSearchRadiusMeters()).isEqualTo(15000.0);
        assertThat(config.epsilonMinutes()).isEqualTo(0.5);
        assertThat(config.serviceBounds()).isEqualTo(bounds());
        assertThat(config.outlierRule()).isEqualTo(rule());
    }

    @Test
    void acceptsAGridDensityOfOne() {
        assertThat(new EngineConfig(bounds(), 1, 15000.0, rule(), 0.5).gridDensityN()).isEqualTo(1);
    }

    @Test
    void rejectsNullServiceBounds() {
        assertThatThrownBy(() -> new EngineConfig(null, 100, 15000.0, rule(), 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serviceBounds");
    }

    @Test
    void rejectsNullOutlierRule() {
        assertThatThrownBy(() -> new EngineConfig(bounds(), 100, 15000.0, null, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outlierRule");
    }

    @Test
    void rejectsGridDensityBelowOne() {
        assertThatThrownBy(() -> new EngineConfig(bounds(), 0, 15000.0, rule(), 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gridDensityN");
    }

    @Test
    void rejectsNonPositiveSearchRadius() {
        assertThatThrownBy(() -> new EngineConfig(bounds(), 100, 0.0, rule(), 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxSearchRadiusMeters");
    }

    @Test
    void rejectsNonFiniteSearchRadius() {
        assertThatThrownBy(() -> new EngineConfig(bounds(), 100, Double.POSITIVE_INFINITY, rule(), 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxSearchRadiusMeters");
    }

    @Test
    void rejectsNonPositiveEpsilon() {
        assertThatThrownBy(() -> new EngineConfig(bounds(), 100, 15000.0, rule(), 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("epsilonMinutes");
    }

    @Test
    void rejectsNonFiniteEpsilon() {
        assertThatThrownBy(() -> new EngineConfig(bounds(), 100, 15000.0, rule(), Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("epsilonMinutes");
    }
}
