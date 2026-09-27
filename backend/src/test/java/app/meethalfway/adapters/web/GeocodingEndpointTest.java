package app.meethalfway.adapters.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.meethalfway.adapters.web.dto.WebMapper;
import app.meethalfway.adapters.web.support.NoOpRoutingProvider;
import app.meethalfway.adapters.web.support.StubRecommendationEngine;
import app.meethalfway.adapters.web.support.TestMeetingRepository;
import app.meethalfway.application.ComputeRecommendations;
import app.meethalfway.application.CreateMeeting;
import app.meethalfway.application.DeleteMeeting;
import app.meethalfway.application.EditMeeting;
import app.meethalfway.application.GetMeeting;
import app.meethalfway.application.UrlCodeGenerator;
import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.EngineConfig;
import app.meethalfway.domain.model.OutlierRule;
import app.meethalfway.domain.model.ServiceBounds;
import app.meethalfway.domain.testing.FakeGeocodingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Geocoding autocomplete endpoint wiring test (Task 10.4, folded in), using the
 * offline {@link FakeGeocodingProvider} so no real Nominatim call is made.
 *
 * <p>Uses standalone MockMvc with real use cases wired to in-memory fakes
 * (Spring 3.3.5's ASM cannot scan JDK 25 bytecode, and the {@code final} use
 * cases cannot be mocked under JDK 25's Byte Buddy). Confirms the
 * {@code /api/v1/geocode/autocomplete} endpoint routes the query and the injected
 * {@link EngineConfig} through the port and maps suggestions to the camelCase
 * wire contract, exposing no coordinate or key.
 */
class GeocodingEndpointTest {

    private final FakeGeocodingProvider geocodingProvider = FakeGeocodingProvider.builder()
            .withAddress("El Poblado, Medellin", "place-poblado", new Coordinate(6.21, -75.57))
            .withAddress("Laureles, Medellin", "place-laureles", new Coordinate(6.24, -75.60))
            .build();

    private final EngineConfig engineConfig = new EngineConfig(
            new ServiceBounds(-90.0, 90.0, -180.0, 180.0),
            100, 20000.0, new OutlierRule.MedianMultiple(2.0), 0.5);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TestMeetingRepository repository = new TestMeetingRepository();
        MeetingController controller = new MeetingController(
                new CreateMeeting(repository, new UrlCodeGenerator()),
                new GetMeeting(repository),
                new EditMeeting(repository),
                new DeleteMeeting(repository),
                new ComputeRecommendations(
                        new StubRecommendationEngine(), repository, new NoOpRoutingProvider(), engineConfig),
                geocodingProvider,
                engineConfig,
                new WebMapper());
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
}
