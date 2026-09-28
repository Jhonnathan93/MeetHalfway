package app.meethalfway.locations.adapters.geocoding;

import app.meethalfway.config.GeocodingProperties;
import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.ServiceBounds;
import app.meethalfway.locations.domain.port.AddressSuggestion;
import app.meethalfway.locations.domain.port.GeocodeResult;
import app.meethalfway.locations.domain.port.GeocodingProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link GeocodingProvider} adapter backed by an OpenStreetMap
 * <strong>Nominatim</strong>-style JSON search endpoint.
 *
 * <p>Nominatim is chosen as the default because it is free and keyless. Its
 * usage policy requires a descriptive {@code User-Agent}/contact string and
 * modest request rates; both are supplied from backend configuration
 * ({@link GeocodingProperties}), never hard-coded. The adapter also accepts an
 * optional API key so a swap to a keyed free-tier provider needs no code change.
 *
 * <p>Requirement 11.4 / 9.7: this adapter runs on the backend so the frontend
 * proxies through it and provider credentials never reach the browser. The base
 * URL, key, User-Agent, and timeout are read from configuration and are never
 * logged or serialized to clients.
 *
 * <p>The single raw HTTP step is delegated to an injected {@link HttpExchange}
 * (JDK {@link java.net.http.HttpClient} in production) so the mapping logic is
 * unit-testable offline.
 *
 * <p>Both operations call the provider {@code /search} endpoint returning JSON:
 * an array of objects carrying {@code display_name}, {@code place_id},
 * {@code lat}, and {@code lon}. {@code autocomplete} maps each entry to an
 * {@link AddressSuggestion}; {@code resolve} takes the first entry's coordinate.
 */
public final class GeocodingAdapter implements GeocodingProvider {

    private final HttpExchange http;
    private final ObjectMapper objectMapper;
    private final GeocodingProperties properties;

    /**
     * Creates the adapter with its collaborators (constructor injection only).
     *
     * @param http         the HTTP transport seam; must not be null
     * @param objectMapper the JSON mapper for parsing provider responses; must not be null
     * @param properties   the backend-only geocoding configuration; must not be null,
     *                     and must carry a non-blank base URL and User-Agent
     */
    public GeocodingAdapter(HttpExchange http, ObjectMapper objectMapper, GeocodingProperties properties) {
        if (http == null) {
            throw new IllegalArgumentException("http must not be null");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            throw new IllegalArgumentException("geocoding baseUrl must be configured");
        }
        if (properties.userAgent() == null || properties.userAgent().isBlank()) {
            throw new IllegalArgumentException("geocoding userAgent must be configured (provider policy)");
        }
        this.http = http;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<AddressSuggestion> autocomplete(String query, EngineConfig config) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        StringBuilder url = new StringBuilder(searchBase());
        url.append("&q=").append(encode(query.trim()));
        url.append("&limit=").append(resultLimit());
        appendBounds(url, config.serviceBounds());

        JsonNode root = query(url.toString());
        if (root == null || !root.isArray()) {
            return List.of();
        }

        List<AddressSuggestion> suggestions = new ArrayList<>();
        for (JsonNode entry : root) {
            String description = text(entry, "display_name");
            String placeId = text(entry, "place_id");
            if (description == null || description.isBlank() || placeId == null || placeId.isBlank()) {
                continue; // skip malformed entries rather than fail the whole query
            }
            suggestions.add(new AddressSuggestion(description, placeId));
        }
        return List.copyOf(suggestions);
    }

    @Override
    public GeocodeResult resolve(String address) {
        if (address == null || address.isBlank()) {
            return GeocodeResult.notFound("Address is empty; enter an address to resolve.");
        }

        String url = searchBase() + "&limit=1&q=" + encode(address.trim());

        JsonNode root;
        try {
            root = queryOrThrow(url);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return GeocodeResult.notFound(
                    "Could not reach the geocoding service; please try again or correct the address.");
        }

        if (root == null || !root.isArray() || root.isEmpty()) {
            return GeocodeResult.notFound("No location found for the given address.");
        }

        JsonNode first = root.get(0);
        Double lat = number(first, "lat");
        Double lng = number(first, "lon");
        if (lat == null || lng == null) {
            return GeocodeResult.notFound("The geocoding service returned no coordinate for this address.");
        }

        try {
            return GeocodeResult.resolved(new Coordinate(lat, lng));
        } catch (IllegalArgumentException invalid) {
            return GeocodeResult.notFound("The resolved coordinate was out of range for this address.");
        }
    }

    // --- helpers -----------------------------------------------------------

    private String searchBase() {
        String base = properties.baseUrl().trim();
        StringBuilder url = new StringBuilder(base);
        url.append(base.contains("?") ? "&" : "?");
        url.append("format=json");
        String key = properties.apiKey();
        if (key != null && !key.isBlank()) {
            // Backend-only; never exposed to the frontend or logs.
            url.append("&key=").append(encode(key));
        }
        return url.toString();
    }

    /**
     * Biases and bounds suggestions to the configured served region so results
     * stay within scope while the logic remains city-agnostic (bounds come from
     * {@link EngineConfig}, not hard-coded).
     */
    private static void appendBounds(StringBuilder url, ServiceBounds bounds) {
        // Nominatim viewbox order: left(minLng),top(maxLat),right(maxLng),bottom(minLat).
        url.append("&viewbox=")
                .append(bounds.minLng()).append(',')
                .append(bounds.maxLat()).append(',')
                .append(bounds.maxLng()).append(',')
                .append(bounds.minLat());
        url.append("&bounded=1");
    }

    private JsonNode query(String url) {
        try {
            return queryOrThrow(url);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // Autocomplete degrades to no suggestions rather than surfacing an error.
            return null;
        }
    }

    private JsonNode queryOrThrow(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMillis()))
                .header("User-Agent", properties.userAgent())
                .header("Accept", "application/json")
                .GET()
                .build();
        String body = http.send(request);
        if (body == null || body.isBlank()) {
            return null;
        }
        return objectMapper.readTree(body);
    }

    private long timeoutMillis() {
        Long configured = properties.timeoutMillis();
        return configured != null && configured > 0 ? configured : 5000L;
    }

    private int resultLimit() {
        Integer configured = properties.resultLimit();
        return configured != null && configured > 0 ? configured : 5;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        try {
            return Double.parseDouble(value.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
