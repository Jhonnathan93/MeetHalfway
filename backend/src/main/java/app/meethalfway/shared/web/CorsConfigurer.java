package app.meethalfway.shared.web;

import java.util.List;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Deliberate CORS configuration for the REST surface (Requirement 11.4).
 *
 * <p>Only the explicitly configured origins are permitted; the wildcard
 * {@code "*"} is never used together with credentials. Mapping is scoped to the
 * versioned API path ({@code /api/v1/**}).
 *
 * <p>The allowed origins/methods/headers are passed as plain lists via
 * constructor injection, so this web-adapter class never depends on the
 * {@code config} layer directly (the composition root derives the lists from
 * {@code MeetHalfwayProperties.Cors} and supplies them here).
 */
public final class CorsConfigurer implements WebMvcConfigurer {

    private final List<String> allowedOrigins;
    private final List<String> allowedMethods;
    private final List<String> allowedHeaders;

    /**
     * @param allowedOrigins the exact origins permitted; must not be null/empty
     * @param allowedMethods the HTTP methods permitted; must not be null/empty
     * @param allowedHeaders the request headers permitted; must not be null/empty
     */
    public CorsConfigurer(
            List<String> allowedOrigins, List<String> allowedMethods, List<String> allowedHeaders) {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("allowedOrigins must not be null or empty");
        }
        if (allowedMethods == null || allowedMethods.isEmpty()) {
            throw new IllegalArgumentException("allowedMethods must not be null or empty");
        }
        if (allowedHeaders == null || allowedHeaders.isEmpty()) {
            throw new IllegalArgumentException("allowedHeaders must not be null or empty");
        }
        this.allowedOrigins = List.copyOf(allowedOrigins);
        this.allowedMethods = List.copyOf(allowedMethods);
        this.allowedHeaders = List.copyOf(allowedHeaders);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods(allowedMethods.toArray(String[]::new))
                .allowedHeaders(allowedHeaders.toArray(String[]::new));
    }
}
