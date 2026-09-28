package app.meethalfway.locations.adapters.places;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.locations.domain.port.PlaceQuery;
import app.meethalfway.locations.domain.port.PlacesProvider;
import org.junit.jupiter.api.Test;

/**
 * Verifies the MVP no-op {@link PlacesProvider} stub (Requirement 12.3): it
 * honors the port contract by validating inputs and always returning an empty,
 * non-null list, without any network calls.
 */
class StubPlacesProviderTest {

    private final PlacesProvider provider = new StubPlacesProvider();
    private final Coordinate center = new Coordinate(6.2442, -75.5812);
    private final PlaceQuery query = new PlaceQuery(500, 10);

    @Test
    void nearbyReturnsEmptyNonNullList() {
        assertThat(provider.nearby(center, query)).isNotNull().isEmpty();
    }

    @Test
    void nearbyRejectsNullCenter() {
        assertThatNullPointerException()
                .isThrownBy(() -> provider.nearby(null, query))
                .withMessageContaining("center");
    }

    @Test
    void nearbyRejectsNullQuery() {
        assertThatNullPointerException()
                .isThrownBy(() -> provider.nearby(center, null))
                .withMessageContaining("query");
    }
}
