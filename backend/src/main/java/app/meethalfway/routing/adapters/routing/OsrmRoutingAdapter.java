package app.meethalfway.routing.adapters.routing;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import app.meethalfway.config.MeetHalfwayProperties.Routing;
import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.shared.domain.TransportMode;
import app.meethalfway.routing.domain.port.RouteResult;
import app.meethalfway.routing.domain.port.RoutingProvider;

/**
 * Routing adapter implementing {@link RoutingProvider} on top of the free /
 * open-source <a href="https://project-osrm.org/">OSRM</a> routing service
 * (see {@code docs/decisions/ADR-001-routing-provider.md}).
 *
 * <p>It calls OSRM's {@code /route/v1/{profile}/{lng},{lat};{lng},{lat}} endpoint,
 * reads the route {@code duration} (seconds), rounds it to whole minutes, and
 * returns a {@link RouteResult.Success}. Every <em>expected</em> failure —
 * request timeout, HTTP 429 rate limiting, any other non-2xx response, and an
 * unreachable / no-route answer — is mapped to a {@link RouteResult.Failure}
 * with a clear, specific reason instead of being thrown (Requirement 6.1). Only
 * genuinely unexpected faults (e.g. a thread interruption) propagate.
 *
 * <p>OSRM's public demo server is keyless, so no secret is required. If a
 * self-hosted or alternative deployment needs a key it is read from backend
 * configuration ({@code meethalfway.routing.api-key}) and sent as a bearer
 * header; the key is never logged, returned, or exposed to the frontend
 * (Requirement 11.4).
 *
 * <p>The adapter is stateless and thread-safe; a single {@link HttpClient} is
 * reused across calls.
 */
public class OsrmRoutingAdapter implements RoutingProvider {

    private static final int SECONDS_PER_MINUTE = 60;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String drivingProfile;
    private final String walkingProfile;
    private final Duration timeout;
    private final String apiKey;

    /**
     * Production constructor: builds an {@link HttpClient} with the configured
     * connect timeout from {@link Routing}.
     *
     * @param properties   the externalized routing configuration (must supply a
     *                     base URL, both profiles, and a positive timeout)
     * @param objectMapper the JSON mapper (Spring-provided) used to parse responses
     */
    public OsrmRoutingAdapter(Routing properties, ObjectMapper objectMapper) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(resolveTimeout(properties))
                        .build(),
                objectMapper,
                properties);
    }

    /**
     * Injectable constructor used by tests to supply a stubbed {@link HttpClient}
     * so no network call is made.
     *
     * @param httpClient   the HTTP client (real or stubbed)
     * @param objectMapper the JSON mapper used to parse responses
     * @param properties   the externalized routing configuration
     */
    OsrmRoutingAdapter(HttpClient httpClient, ObjectMapper objectMapper, Routing properties) {
        this.httpClient = requireNonNull(httpClient, "httpClient");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper");
        this.baseUrl = requireBaseUrl(properties);
        this.drivingProfile = requireProfile(properties.drivingProfile(), "drivingProfile");
        this.walkingProfile = requireProfile(properties.walkingProfile(), "walkingProfile");
        this.timeout = resolveTimeout(properties);
        // May be null/blank: the public OSRM demo server is keyless.
        this.apiKey = properties.apiKey();
    }

    @Override
    public RouteResult travelTime(Coordinate origin, Coordinate destination, TransportMode mode) {
        if (origin == null || destination == null || mode == null) {
            throw new IllegalArgumentException("origin, destination and mode must not be null");
        }

        final URI uri;
        try {
            uri = URI.create(buildRequestUrl(origin, destination, mode));
        } catch (RuntimeException e) {
            // A malformed URL is a configuration/programming fault, not an
            // expected routing failure; surface it explicitly.
            throw new IllegalStateException("failed to build routing request URL", e);
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET();
        if (apiKey != null && !apiKey.isBlank()) {
            // Sent to the routing service only; never echoed back to callers.
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        final HttpResponse<String> response;
        try {
            response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            // HttpConnectTimeoutException is a subclass of HttpTimeoutException,
            // so this single catch covers both connect and request timeouts.
            return RouteResult.failure(
                    "Routing request timed out after " + timeout.toMillis() + " ms.");
        } catch (java.io.IOException e) {
            return RouteResult.failure(
                    "Could not reach the routing service: " + e.getMessage());
        } catch (InterruptedException e) {
            // Preserve interruption status and let it propagate: this is an
            // unexpected control-flow signal, not a routing outcome.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("routing request was interrupted", e);
        }

        return interpret(response);
    }

    private RouteResult interpret(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == HTTP_TOO_MANY_REQUESTS) {
            return RouteResult.failure(
                    "Routing service rate limit exceeded (HTTP 429). Please retry shortly.");
        }
        if (status < 200 || status >= 300) {
            return RouteResult.failure(
                    "Routing service returned an unexpected status (HTTP " + status + ").");
        }

        final JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (Exception e) {
            return RouteResult.failure("Routing service returned an unreadable response.");
        }

        String code = root.path("code").asText("");
        if (!"Ok".equals(code)) {
            // OSRM signals no route with codes such as "NoRoute"/"NoSegment".
            String message = root.path("message").asText("");
            String detail = message.isBlank() ? code : code + " - " + message;
            return RouteResult.failure(
                    "No route could be computed for this location (" + detail + ").");
        }

        JsonNode routes = root.path("routes");
        if (!routes.isArray() || routes.isEmpty()) {
            return RouteResult.failure("No route could be computed for this location.");
        }

        JsonNode durationNode = routes.get(0).path("duration");
        if (durationNode.isMissingNode() || !durationNode.isNumber()) {
            return RouteResult.failure("Routing service response was missing a travel duration.");
        }

        double seconds = durationNode.asDouble();
        if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 0) {
            return RouteResult.failure("Routing service returned an invalid travel duration.");
        }

        int minutes = (int) Math.round(seconds / SECONDS_PER_MINUTE);
        return RouteResult.success(new Minutes(minutes));
    }

    private String buildRequestUrl(Coordinate origin, Coordinate destination, TransportMode mode) {
        String profile = profileFor(mode);
        // OSRM expects lng,lat order; overview=false keeps the response small since
        // we only need the duration.
        return baseUrl
                + "/route/v1/" + profile + "/"
                + format(origin.lng()) + "," + format(origin.lat())
                + ";"
                + format(destination.lng()) + "," + format(destination.lat())
                + "?overview=false&alternatives=false&steps=false";
    }

    private String profileFor(TransportMode mode) {
        return switch (mode) {
            case DRIVING -> drivingProfile;
            case WALKING -> walkingProfile;
        };
    }

    private static String format(double value) {
        // Fixed decimal formatting with '.' regardless of locale, avoiding
        // scientific notation in the URL path.
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static Duration resolveTimeout(Routing properties) {
        Long millis = properties == null ? null : properties.timeoutMillis();
        if (millis == null || millis <= 0) {
            throw new IllegalArgumentException(
                    "meethalfway.routing.timeout-millis must be a positive value");
        }
        return Duration.ofMillis(millis);
    }

    private static String requireBaseUrl(Routing properties) {
        if (properties == null || properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            throw new IllegalArgumentException("meethalfway.routing.base-url must be configured");
        }
        String url = properties.baseUrl().trim();
        // Normalize away a trailing slash so path concatenation stays clean.
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String requireProfile(String profile, String name) {
        if (profile == null || profile.isBlank()) {
            throw new IllegalArgumentException("meethalfway.routing." + name + " must be configured");
        }
        return profile.trim();
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
