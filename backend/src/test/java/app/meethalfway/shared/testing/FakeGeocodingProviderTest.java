package app.meethalfway.shared.testing;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.OutlierRule;
import app.meethalfway.meetings.domain.model.ServiceBounds;
import app.meethalfway.locations.domain.port.AddressSuggestion;
import app.meethalfway.locations.domain.port.GeocodeResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FakeGeocodingProvider}, verifying reproducible
 * autocomplete suggestions and address resolution without any network call.
 */
class FakeGeocodingProviderTest {

    private static final Coordinate INSIDE = new Coordinate(6.24, -75.57);
    private static final Coordinate OUTSIDE = new Coordinate(4.60, -74.08);

    private static EngineConfig config() {
        return new EngineConfig(
                new ServiceBounds(6.10, 6.40, -75.70, -75.40),
                25,
                15000.0,
                new OutlierRule(2.0),
                0.5);
    }

    @Test
    void autocompleteReturnsCaseInsensitiveMatchesInInsertionOrder() {
        FakeGeocodingProvider geocoding = FakeGeocodingProvider.builder()
                .withAddress("Parque Lleras", "place-lleras", INSIDE)
                .withAddress("Parque Berrio", "place-berrio", INSIDE)
                .withAddress("Estadio", "place-estadio", INSIDE)
                .build();

        List<AddressSuggestion> matches = geocoding.autocomplete("parque", config());

        assertThat(matches).extracting(AddressSuggestion::placeId)
                .containsExactly("place-lleras", "place-berrio");
    }

    @Test
    void autocompleteReturnsEmptyForBlankQuery() {
        FakeGeocodingProvider geocoding = FakeGeocodingProvider.builder()
                .withAddress("Parque Lleras", "place-lleras", INSIDE)
                .build();

        assertThat(geocoding.autocomplete("   ", config())).isEmpty();
    }

    @Test
    void resolveReturnsRegisteredCoordinate() {
        FakeGeocodingProvider geocoding = FakeGeocodingProvider.builder()
                .withAddress("Parque Lleras", "place-lleras", INSIDE)
                .build();

        GeocodeResult byDescription = geocoding.resolve("Parque Lleras");
        GeocodeResult byPlaceId = geocoding.resolve("place-lleras");

        assertThat(byDescription).isInstanceOf(GeocodeResult.Resolved.class);
        assertThat(((GeocodeResult.Resolved) byDescription).coordinate()).isEqualTo(INSIDE);
        assertThat(byPlaceId).isEqualTo(byDescription);
    }

    @Test
    void resolveReturnsNotFoundForUnregisteredAddress() {
        FakeGeocodingProvider geocoding = FakeGeocodingProvider.empty();

        assertThat(geocoding.resolve("nowhere")).isInstanceOf(GeocodeResult.NotFound.class);
    }

    @Test
    void resolveHonorsExplicitlyUnresolvableAddress() {
        FakeGeocodingProvider geocoding = FakeGeocodingProvider.builder()
                .withUnresolvable("ambiguous street", "address is ambiguous")
                .build();

        GeocodeResult result = geocoding.resolve("ambiguous street");

        assertThat(result).isInstanceOf(GeocodeResult.NotFound.class);
        assertThat(((GeocodeResult.NotFound) result).reason()).isEqualTo("address is ambiguous");
    }

    @Test
    void serviceBoundsRestrictionFiltersOutOfBoundsSuggestions() {
        FakeGeocodingProvider geocoding = FakeGeocodingProvider.builder()
                .withAddress("Parque Lleras", "place-lleras", INSIDE)
                .withAddress("Parque Bogota", "place-bogota", OUTSIDE)
                .restrictAutocompleteToServiceBounds()
                .build();

        List<AddressSuggestion> matches = geocoding.autocomplete("parque", config());

        assertThat(matches).extracting(AddressSuggestion::placeId).containsExactly("place-lleras");
    }
}
