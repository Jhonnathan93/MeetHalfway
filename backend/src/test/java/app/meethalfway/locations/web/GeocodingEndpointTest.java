package app.meethalfway.locations.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.OutlierRule;
import app.meethalfway.meetings.domain.model.ServiceBounds;
import app.meethalfway.shared.testing.FakeGeocodingProvider;
import app.meethalfway.locations.web.dto.LocationMapper;
import app.meethalfway.shared.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Geocoding endpoint wiring test for {@link LocationController} (Task 10.4,
 * folded in; retargeted from {@code MeetingController} when the geocoding
 * endpoints moved to the locations module in task 4.1), using the offline
 * {@link FakeGeocodingProvider} so no real Nominatim call is made.
 *
 * <p>Uses standalone MockMvc with the real {@link LocationController} wired to
 * an in-memory fake provider (Spring 3.3.5's ASM cannot scan JDK 25 bytecode).
 * Confirms the {@code /api/v1/geocode/autocomplete} and
 * {@code /api/v1/geocode/resolve} endpoints route the query and the injected
 * {@link EngineConfig} through the port and map suggestions/coordinates to the
 * camelCase wire contract, exposing no coordinate or key on autocomplete and no
 * key on resolve.
 */
class GeocodingEndpointTest {

    private final FakeGeocodingProvider geocodingProvider = FakeGeocodingProvider.builder()
            .withAddress("El Poblado, Medellin", "place-poblado", new Coordinate(6.21, -75.57))
            .withAddress("Laureles, Medellin", "place-laureles", new Coordinate(6.24, -75.60))
            .build();

    private final EngineConfig engineConfig = new EngineConfig(
            new ServiceBounds(-90.0, 90.0, -180.0, 180.0),
            100, 20000.0, new OutlierRule(2.0), 0.5);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocationController controller = new LocationController(
                geocodingProvider, engineConfig, new LocationMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void autocompleteReturnsMappedSuggestionsFromFakeProvider() throws Exception {
        mockMvc.perform(get("/api/v1/geocode/autocomplete").param("q", "poblado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("El Poblado, Medellin"))
                .andExpect(jsonPath("$[0].placeId").value("place-poblado"));
    }

    @Test
    void autocompleteReturnsEmptyListForNoMatch() throws Exception {
        mockMvc.perform(get("/api/v1/geocode/autocomplete").param("q", "nowhere-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void autocompleteResponseNeverContainsACoordinateOrKey() throws Exception {
        mockMvc.perform(get("/api/v1/geocode/autocomplete").param("q", "medellin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lat").doesNotExist())
                .andExpect(jsonPath("$[0].lng").doesNotExist())
                .andExpect(jsonPath("$[0].apiKey").doesNotExist());
    }

    @Test
    void resolveReturnsCoordinateForAKnownAddress() throws Exception {
        mockMvc.perform(get("/api/v1/geocode/resolve").param("q", "El Poblado, Medellin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lat").value(6.21))
                .andExpect(jsonPath("$.lng").value(-75.57));
    }

    @Test
    void resolveReturns422WithReasonForAnUnknownAddress() throws Exception {
        mockMvc.perform(get("/api/v1/geocode/resolve").param("q", "nowhere-xyz-address"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GEOCODE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void resolveResponseNeverExposesAProviderKey() throws Exception {
        mockMvc.perform(get("/api/v1/geocode/resolve").param("q", "El Poblado, Medellin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }
}
