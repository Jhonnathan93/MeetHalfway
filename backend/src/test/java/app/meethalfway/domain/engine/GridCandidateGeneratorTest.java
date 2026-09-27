package app.meethalfway.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.EngineConfig;
import app.meethalfway.domain.model.OutlierRule;
import app.meethalfway.domain.model.ServiceBounds;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Example-based unit tests for {@link GridCandidateGenerator}. Property 11
 * (exactly N points, all in-region) is covered by a separate property test
 * (task 4.3); these tests pin down concrete behavior and edge cases.
 */
class GridCandidateGeneratorTest {

    private final GridCandidateGenerator generator = new GridCandidateGenerator();

    private static ServiceBounds bounds() {
        return new ServiceBounds(6.0, 6.5, -75.7, -75.4);
    }

    private static EngineConfig config(int gridDensityN) {
        return new EngineConfig(bounds(), gridDensityN, 15000.0, new OutlierRule.MedianMultiple(2.0), 0.5);
    }

    private static List<Coordinate> origins() {
        return List.of(new Coordinate(6.2, -75.6), new Coordinate(6.3, -75.5));
    }

    @Test
    void generatesExactlyNPoints() {
        assertThat(generator.generate(origins(), config(1))).hasSize(1);
        assertThat(generator.generate(origins(), config(7))).hasSize(7);
        assertThat(generator.generate(origins(), config(100))).hasSize(100);
    }

    @Test
    void allPointsLieWithinTheSearchRegion() {
        EngineConfig config = config(50);
        SearchRegion region = generator.searchRegion(origins(), config);
        assertThat(generator.generate(origins(), config)).allSatisfy(c -> assertThat(region.contains(c)).isTrue());
    }

    @Test
    void isDeterministicAcrossRuns() {
        EngineConfig config = config(37);
        assertThat(generator.generate(origins(), config)).isEqualTo(generator.generate(origins(), config));
    }

    @Test
    void searchRegionStaysWithinServiceBounds() {
        SearchRegion region = generator.searchRegion(origins(), config(10));
        assertThat(region.minLat()).isGreaterThanOrEqualTo(bounds().minLat());
        assertThat(region.maxLat()).isLessThanOrEqualTo(bounds().maxLat());
        assertThat(region.minLng()).isGreaterThanOrEqualTo(bounds().minLng());
        assertThat(region.maxLng()).isLessThanOrEqualTo(bounds().maxLng());
    }

    @Test
    void singlePointIsTheRegionCentre() {
        EngineConfig config = config(1);
        SearchRegion region = generator.searchRegion(origins(), config);
        Coordinate point = generator.generate(origins(), config).get(0);
        assertThat(point.lat()).isEqualTo((region.minLat() + region.maxLat()) / 2.0);
        assertThat(point.lng()).isEqualTo((region.minLng() + region.maxLng()) / 2.0);
    }

    @Test
    void rejectsNullOrEmptyOrigins() {
        assertThatThrownBy(() -> generator.generate(null, config(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate(List.of(), config(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullConfig() {
        assertThatThrownBy(() -> generator.generate(origins(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
