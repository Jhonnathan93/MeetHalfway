package app.meethalfway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed settings for all application-owned {@code meethalfway.*} properties. */
@ConfigurationProperties(prefix = "meethalfway")
public record MeetHalfwayProperties(
        Engine engine,
        Routing routing,
        Geocoding geocoding,
        RateLimit rateLimit,
        Cors cors) {

    public record Engine(
            Double serviceBoundsMinLat,
            Double serviceBoundsMaxLat,
            Double serviceBoundsMinLng,
            Double serviceBoundsMaxLng,
            Integer gridDensityN,
            Double maxSearchRadiusMeters,
            Double epsilonMinutes,
            Double outlierMedianMultipleK) {}

    public record Routing(
            String baseUrl,
            String drivingProfile,
            String walkingProfile,
            Long timeoutMillis,
            String apiKey) {
        @Override
        public String toString() {
            return "Routing[baseUrl=" + baseUrl
                    + ", drivingProfile=" + drivingProfile
                    + ", walkingProfile=" + walkingProfile
                    + ", timeoutMillis=" + timeoutMillis
                    + ", apiKey=" + redact(apiKey) + "]";
        }
    }

    public record Geocoding(
            String baseUrl,
            String apiKey,
            String userAgent,
            Long timeoutMillis,
            Integer resultLimit) {
        @Override
        public String toString() {
            return "Geocoding[baseUrl=" + baseUrl
                    + ", apiKey=" + redact(apiKey)
                    + ", userAgent=" + userAgent
                    + ", timeoutMillis=" + timeoutMillis
                    + ", resultLimit=" + resultLimit + "]";
        }
    }

    public record RateLimit(Boolean enabled, Integer requestsPerWindow, Integer windowSeconds) {
        public boolean enabledOrDefault() {
            return enabled == null || enabled;
        }

        public int requestsPerWindowOrDefault() {
            return requestsPerWindow != null ? requestsPerWindow : 60;
        }

        public int windowSecondsOrDefault() {
            return windowSeconds != null ? windowSeconds : 60;
        }
    }

    public record Cors(List<String> allowedOrigins, List<String> allowedMethods, List<String> allowedHeaders) {
        public List<String> allowedOriginsOrDefault() {
            return allowedOrigins == null || allowedOrigins.isEmpty()
                    ? List.of("http://localhost:5173") : allowedOrigins;
        }

        public List<String> allowedMethodsOrDefault() {
            return allowedMethods == null || allowedMethods.isEmpty()
                    ? List.of("GET", "POST", "PUT", "DELETE", "OPTIONS") : allowedMethods;
        }

        public List<String> allowedHeadersOrDefault() {
            return allowedHeaders == null || allowedHeaders.isEmpty()
                    ? List.of("Content-Type", "Accept") : allowedHeaders;
        }
    }

    private static String redact(String value) {
        return value == null || value.isBlank() ? "<none>" : "<redacted>";
    }
}
