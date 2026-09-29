package app.meethalfway.locations.web;

import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.locations.domain.port.AddressSuggestion;
import app.meethalfway.locations.domain.port.GeocodeResult;
import app.meethalfway.locations.domain.port.GeocodingProvider;
import app.meethalfway.locations.web.dto.AddressSuggestionResponse;
import app.meethalfway.locations.web.dto.LocationMapper;
import app.meethalfway.shared.web.dto.ErrorResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound REST adapter for the locations (geocoding) module (Requirements 1, 7,
 * 9.7, 11.4). Exposes the versioned geocoding surface under {@code /api/v1/geocode}
 * with kebab-case path segments and camelCase JSON. Split out of
 * {@code MeetingController} so no single controller serves both the meetings and
 * the geocoding concerns (R2.5).
 *
 * <p>Dependencies are supplied by constructor injection only (no field
 * injection); the controller depends solely on the domain
 * {@link GeocodingProvider} port, the domain {@link EngineConfig}, and the
 * {@link LocationMapper} &mdash; never on an adapter, the {@code config} layer,
 * or an engine class. It performs no geocoding logic itself: each handler binds
 * the request, delegates to the port, and maps the result through the mapper
 * (thin controller, R4.1).
 *
 * <p>The external paths {@code GET /api/v1/geocode/autocomplete} and
 * {@code GET /api/v1/geocode/resolve} are preserved exactly (Behavior_Preservation).
 */
@RestController
@RequestMapping("/api/v1/geocode")
public class LocationController {

    private final GeocodingProvider geocodingProvider;
    private final EngineConfig engineConfig;
    private final LocationMapper locationMapper;

    /**
     * @param geocodingProvider geocoding port for autocomplete/resolve; must not be null
     * @param engineConfig      engine configuration for city-agnostic scoping; must not be null
     * @param locationMapper    geocoding DTO mapper; must not be null
     */
    public LocationController(
            GeocodingProvider geocodingProvider,
            EngineConfig engineConfig,
            LocationMapper locationMapper) {
        if (geocodingProvider == null) {
            throw new IllegalArgumentException("geocodingProvider must not be null");
        }
        if (engineConfig == null) {
            throw new IllegalArgumentException("engineConfig must not be null");
        }
        if (locationMapper == null) {
            throw new IllegalArgumentException("locationMapper must not be null");
        }
        this.geocodingProvider = geocodingProvider;
        this.engineConfig = engineConfig;
        this.locationMapper = locationMapper;
    }

    /**
     * Proposes address autocomplete suggestions as the Creator types
     * (Requirement 9.7). Returns 200 with the suggestions; never exposes provider
     * keys. City-agnostic scoping is delegated to the provider via the injected
     * {@link EngineConfig}.
     *
     * @param query the partial address text (query parameter {@code q})
     * @return 200 with the list of suggestions
     */
    @GetMapping("/autocomplete")
    public ResponseEntity<List<AddressSuggestionResponse>> autocomplete(
            @RequestParam("q") String query) {
        List<AddressSuggestion> suggestions = geocodingProvider.autocomplete(query, engineConfig);
        return ResponseEntity.ok(locationMapper.toSuggestionResponses(suggestions));
    }

    /**
     * Resolves a chosen address or provider place reference to a precise coordinate
     * (Requirement 9.7). Returns 200 with the coordinate when resolved, or 422
     * with an actionable reason when the address could not be resolved so the
     * Creator can correct it &mdash; never a silently wrong location. Provider
     * keys are never exposed. The backend performs the resolution so no provider
     * credential reaches the browser (Requirement 11.4).
     *
     * @param query the free-form address or opaque provider reference (query
     *              parameter {@code q})
     * @return 200 with the resolved coordinate, or 422 with the reason it failed
     */
    @GetMapping("/resolve")
    public ResponseEntity<?> resolve(@RequestParam("q") String query) {
        GeocodeResult result = geocodingProvider.resolve(query);
        if (result instanceof GeocodeResult.Resolved resolved) {
            return ResponseEntity.ok(locationMapper.toCoordinateResponse(resolved.coordinate()));
        }
        GeocodeResult.NotFound notFound = (GeocodeResult.NotFound) result;
        return ResponseEntity.unprocessableEntity()
                .body(ErrorResponse.of("GEOCODE_NOT_FOUND", notFound.reason()));
    }
}
