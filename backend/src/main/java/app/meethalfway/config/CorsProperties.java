package app.meethalfway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized CORS settings (prefix {@code meethalfway.cors}); Requirement
 * 11.4 (configure CORS deliberately).
 *
 * <p>Only the explicitly configured origins are permitted &mdash; the wildcard
 * {@code "*"} is never combined with credentials. The Vite dev origin
 * ({@code http://localhost:5173}) and a placeholder production origin are the
 * defaults; deployments override the list via the environment.
 *
 * <p>This is a framework-facing binding record in the {@code config} layer; the
 * web adapter's {@code WebMvcConfigurer} consumes the derived values by
 * constructor injection.
 *
 * @param allowedOrigins the exact origins allowed to call the API
 * @param allowedMethods the HTTP methods allowed for cross-origin requests
 * @param allowedHeaders the request headers allowed for cross-origin requests
 */
@ConfigurationProperties(prefix = "meethalfway.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders) {

    /** Default allowed origins: the Vite dev server plus a prod placeholder. */
    private static final List<String> DEFAULT_ORIGINS =
            List.of("http://localhost:5173");

    /** Default allowed methods covering the REST surface. */
    private static final List<String> DEFAULT_METHODS =
            List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");

    /** Default allowed headers. */
    private static final List<String> DEFAULT_HEADERS =
            List.of("Content-Type", "Accept");

    /**
     * @return the configured allowed origins, or the safe defaults when unset;
     *         never contains the wildcard by default
     */
    public List<String> allowedOriginsOrDefault() {
        return allowedOrigins == null || allowedOrigins.isEmpty() ? DEFAULT_ORIGINS : allowedOrigins;
    }

    /**
     * @return the configured allowed methods, or the defaults when unset
     */
    public List<String> allowedMethodsOrDefault() {
        return allowedMethods == null || allowedMethods.isEmpty() ? DEFAULT_METHODS : allowedMethods;
    }

    /**
     * @return the configured allowed headers, or the defaults when unset
     */
    public List<String> allowedHeadersOrDefault() {
        return allowedHeaders == null || allowedHeaders.isEmpty() ? DEFAULT_HEADERS : allowedHeaders;
    }
}
