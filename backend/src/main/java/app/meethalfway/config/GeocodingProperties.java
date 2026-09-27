package app.meethalfway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized settings for the geocoding provider (prefix
 * {@code meethalfway.geocoding}).
 *
 * <p>Requirement 11.4: provider credentials and access details live only in
 * backend runtime configuration. They are read here from the environment and
 * never hard-coded, never logged, and never shipped to the frontend. The
 * default provider is keyless (OpenStreetMap Nominatim), so {@code apiKey} is
 * optional; it exists so a swap to a free-tier keyed provider needs no code
 * change.
 *
 * <p>This is a framework-facing binding record in the {@code config} layer; the
 * {@code GeocodingAdapter} in the {@code adapters} layer receives it by
 * constructor injection. Fields are boxed/nullable so missing configuration is
 * detectable rather than silently defaulted.
 *
 * @param baseUrl       the provider base URL (e.g. a Nominatim endpoint); must be
 *                      configured, never hard-coded
 * @param apiKey        optional API key for keyed free-tier providers; omitted for
 *                      keyless Nominatim. Never logged or serialized to clients
 * @param userAgent     the {@code User-Agent}/contact string the provider's usage
 *                      policy requires (Nominatim mandates a valid identifier)
 * @param timeoutMillis per-request HTTP timeout in milliseconds
 * @param resultLimit   maximum number of autocomplete suggestions to request
 */
@ConfigurationProperties(prefix = "meethalfway.geocoding")
public record GeocodingProperties(
        String baseUrl,
        String apiKey,
        String userAgent,
        Long timeoutMillis,
        Integer resultLimit) {

    /**
     * Never expose the API key through {@code toString()} (used by logging and
     * actuator). Only non-secret fields are rendered.
     */
    @Override
    public String toString() {
        return "GeocodingProperties[baseUrl=" + baseUrl
                + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "<none>" : "<redacted>")
                + ", userAgent=" + userAgent
                + ", timeoutMillis=" + timeoutMillis
                + ", resultLimit=" + resultLimit + "]";
    }
}
