package app.meethalfway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized settings for the routing adapter, bound from configuration
 * (prefix {@code meethalfway.routing}).
 *
 * <p>The MVP uses the free / open-source OSRM routing service (see
 * {@code docs/decisions/ADR-001-routing-provider.md}). The public OSRM demo
 * server is <strong>keyless</strong>, so {@link #apiKey()} is optional and is
 * present only to support a self-hosted or alternative deployment that requires
 * one. When an API key <em>is</em> configured it is supplied through the backend
 * runtime environment only and is never returned, logged, or serialized
 * (Requirement 11.4).
 *
 * <p>All fields are boxed/nullable so missing configuration is detectable rather
 * than silently defaulted; the adapter validates required values at construction.
 *
 * @param baseUrl        the routing service base URL, e.g.
 *                       {@code https://router.project-osrm.org}
 * @param drivingProfile the OSRM profile name for {@code DRIVING} (e.g. {@code driving})
 * @param walkingProfile the OSRM profile name for {@code WALKING} (e.g. {@code walking} or {@code foot})
 * @param timeoutMillis  per-request timeout in milliseconds; a timeout maps to a
 *                       routing {@code Failure} rather than a thrown exception
 * @param apiKey         optional backend-only API key (null/blank when the
 *                       keyless public OSRM demo server is used); never exposed
 *                       to the frontend
 */
@ConfigurationProperties(prefix = "meethalfway.routing")
public record RoutingProperties(
        String baseUrl,
        String drivingProfile,
        String walkingProfile,
        Long timeoutMillis,
        String apiKey
) {
}
