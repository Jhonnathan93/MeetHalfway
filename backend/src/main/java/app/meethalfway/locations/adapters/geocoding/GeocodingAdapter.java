package app.meethalfway.locations.adapters.geocoding;

import app.meethalfway.config.MeetHalfwayProperties.Geocoding;
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
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * {@link GeocodingProvider} adapter backed by an OpenStreetMap
 * <strong>Nominatim</strong>-style JSON search endpoint.
 *
 * <p>Nominatim is chosen as the default because it is free and keyless. Its
 * usage policy requires a descriptive {@code User-Agent}/contact string and
 * modest request rates; both are supplied from backend configuration
 * ({@link Geocoding}), never hard-coded. The adapter also accepts an
 * optional API key so a swap to a keyed free-tier provider needs no code change.
 *
 * <p>Requirement 11.4 / 9.7: this adapter runs on the backend so the frontend
 * proxies through it and provider credentials never reach the browser. The base
 * URL, key, User-Agent, and timeout are read from configuration and are never
 * logged or serialized to clients.
 *
 * <p>The JDK {@link HttpClient} performs the provider request directly; no
 * generic transport wrapper is needed at this external-system boundary.
 *
 * <p>Autocomplete uses Nominatim's {@code /search} endpoint and returns an OSM
 * reference with each label. Resolving a selected suggestion uses
 * {@code /lookup} with that reference, avoiding a second fuzzy search on the
 * potentially long display label. Free-form resolve queries still use
 * {@code /search}.
 */
public final class GeocodingAdapter implements GeocodingProvider {

    private static final Pattern OSM_REFERENCE = Pattern.compile("[NWR]\\d+");

    private final HttpClient http;
    private final ObjectMapper objectMapper;
    private final Geocoding properties;

    /**
     * Creates the adapter with its collaborators (constructor injection only).
     *
     * @param http         the HTTP client; must not be null
     * @param objectMapper the JSON mapper for parsing provider responses; must not be null
     * @param properties   the backend-only geocoding configuration; must not be null,
     *                     and must carry a non-blank base URL and User-Agent
     */
    public GeocodingAdapter(HttpClient http, ObjectMapper objectMapper, Geocoding properties) {
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

        StringBuilder url = new StringBuilder(endpoint("search"));
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
            String placeId = osmReference(entry);
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

        String value = address.trim();
        String url = OSM_REFERENCE.matcher(value).matches()
                ? endpoint("lookup") + "&osm_ids=" + encode(value)
                : endpoint("search") + "&limit=1&q=" + encode(value);

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

    private String endpoint(String operation) {
        String base = properties.baseUrl().trim();
        int queryStart = base.indexOf('?');
        String existingQuery = queryStart < 0 ? "" : base.substring(queryStart + 1);
        String path = queryStart < 0 ? base : base.substring(0, queryStart);
        path = path.replaceAll("/+$", "");

        // Accept either the Nominatim server root or an explicitly configured
        // /search or /lookup URL. The operation path is always explicit.
        if (path.endsWith("/search") || path.endsWith("/lookup")) {
            path = path.substring(0, path.lastIndexOf('/'));
        }

        StringBuilder url = new StringBuilder(path).append('/').append(operation).append('?');
        if (!existingQuery.isBlank()) {
            url.append(existingQuery).append('&');
        }
        if (!existingQuery.contains("format=")) {
            url.append("format=json");
        } else if (url.charAt(url.length() - 1) == '&') {
            url.setLength(url.length() - 1);
        }
        String key = properties.apiKey();
        if (key != null && !key.isBlank()) {
            // Backend-only; never exposed to the frontend or logs.
            if (url.charAt(url.length() - 1) != '?') {
                url.append('&');
            }
            url.append("key=").append(encode(key));
        }
        return url.toString();
    }

    private static String osmReference(JsonNode entry) {
        String type = text(entry, "osm_type");
        String id = text(entry, "osm_id");
        if (id == null || !id.matches("\\d+")) {
            return null;
        }
        String prefix = switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
            case "node" -> "N";
            case "way" -> "W";
            case "relation" -> "R";
            default -> null;
        };
        return prefix == null ? null : prefix + id;
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
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("geocoding provider returned HTTP status " + response.statusCode());
        }
        String body = response.body();
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
