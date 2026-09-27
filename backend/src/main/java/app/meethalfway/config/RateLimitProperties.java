package app.meethalfway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized settings for the hand-rolled request rate limiter (prefix
 * {@code meethalfway.rate-limit}); Requirement 11.4 (rate-limit exposed
 * endpoints).
 *
 * <p>The limiter is a plain in-memory fixed-window counter keyed by client IP;
 * no third-party library is used. These values tune it without code changes.
 * Fields are boxed/nullable so a missing value can be defaulted deterministically
 * at the composition root rather than silently binding to {@code 0}.
 *
 * <p>This is a framework-facing binding record in the {@code config} layer. The
 * filter lives in the web adapter and receives the <em>derived</em> settings by
 * constructor injection, so the adapter never depends on this config record
 * directly (preserving the layered dependency rule).
 *
 * @param enabled           whether rate limiting is active; defaults to {@code true}
 * @param requestsPerWindow the maximum number of requests allowed per window per
 *                          client IP; defaults to {@value #DEFAULT_REQUESTS_PER_WINDOW}
 * @param windowSeconds     the fixed window length in seconds; defaults to
 *                          {@value #DEFAULT_WINDOW_SECONDS}
 */
@ConfigurationProperties(prefix = "meethalfway.rate-limit")
public record RateLimitProperties(
        Boolean enabled,
        Integer requestsPerWindow,
        Integer windowSeconds) {

    /** Default request budget per window when unset. */
    public static final int DEFAULT_REQUESTS_PER_WINDOW = 60;

    /** Default fixed-window length in seconds when unset. */
    public static final int DEFAULT_WINDOW_SECONDS = 60;

    /**
     * @return whether the limiter is enabled, defaulting to {@code true}
     */
    public boolean enabledOrDefault() {
        return enabled == null || enabled;
    }

    /**
     * @return the per-window request budget, defaulting to
     *         {@value #DEFAULT_REQUESTS_PER_WINDOW}
     */
    public int requestsPerWindowOrDefault() {
        return requestsPerWindow != null ? requestsPerWindow : DEFAULT_REQUESTS_PER_WINDOW;
    }

    /**
     * @return the window length in seconds, defaulting to
     *         {@value #DEFAULT_WINDOW_SECONDS}
     */
    public int windowSecondsOrDefault() {
        return windowSeconds != null ? windowSeconds : DEFAULT_WINDOW_SECONDS;
    }
}
