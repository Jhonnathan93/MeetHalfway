package app.meethalfway.config;

import app.meethalfway.locations.adapters.geocoding.GeocodingAdapter;
import app.meethalfway.locations.domain.port.GeocodingProvider;
import app.meethalfway.meetings.domain.engine.GridCandidateGenerator;
import app.meethalfway.meetings.domain.engine.MeetingValidator;
import app.meethalfway.meetings.domain.engine.MetricCalculator;
import app.meethalfway.meetings.domain.engine.OutlierDetector;
import app.meethalfway.meetings.domain.engine.RecommendationEngine;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.OutlierRule;
import app.meethalfway.meetings.domain.model.ServiceBounds;
import app.meethalfway.routing.adapters.routing.OsrmRoutingAdapter;
import app.meethalfway.routing.domain.port.RoutingProvider;
import app.meethalfway.shared.web.CorsConfigurer;
import app.meethalfway.shared.web.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires pure domain objects and infrastructure requiring explicit construction. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MeetHalfwayProperties.class)
public class BeanConfiguration {

    @Bean
    RoutingProvider routingProvider(MeetHalfwayProperties properties, ObjectMapper objectMapper) {
        return new OsrmRoutingAdapter(properties.routing(), objectMapper);
    }

    @Bean
    GeocodingProvider geocodingProvider(MeetHalfwayProperties properties, ObjectMapper objectMapper) {
        var geocoding = properties.geocoding();
        long timeoutMillis = geocoding.timeoutMillis() != null ? geocoding.timeoutMillis() : 5000L;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMillis))
                .build();
        return new GeocodingAdapter(httpClient, objectMapper, geocoding);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RecommendationEngine recommendationEngine() {
        return new RecommendationEngine(
                new MeetingValidator(), new GridCandidateGenerator(), new MetricCalculator(), new OutlierDetector());
    }

    @Bean
    EngineConfig engineConfig(MeetHalfwayProperties properties) {
        var engine = properties.engine();
        ServiceBounds bounds = new ServiceBounds(
                require(engine.serviceBoundsMinLat(), "service-bounds-min-lat"),
                require(engine.serviceBoundsMaxLat(), "service-bounds-max-lat"),
                require(engine.serviceBoundsMinLng(), "service-bounds-min-lng"),
                require(engine.serviceBoundsMaxLng(), "service-bounds-max-lng"));
        int gridDensity = requireInt(engine.gridDensityN(), "grid-density-n");
        double radius = require(engine.maxSearchRadiusMeters(), "max-search-radius-meters");
        double epsilon = require(engine.epsilonMinutes(), "epsilon-minutes");
        double outlierK = engine.outlierMedianMultipleK() != null ? engine.outlierMedianMultipleK() : 2.0;
        return new EngineConfig(bounds, gridDensity, radius, new OutlierRule(outlierK), epsilon);
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            MeetHalfwayProperties properties, ObjectMapper objectMapper) {
        var settings = properties.rateLimit();
        RateLimitFilter filter = new RateLimitFilter(
                settings.enabledOrDefault(),
                settings.requestsPerWindowOrDefault(),
                settings.windowSecondsOrDefault(),
                objectMapper);
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/*");
        registration.setName("rateLimitFilter");
        return registration;
    }

    @Bean
    CorsConfigurer corsConfigurer(MeetHalfwayProperties properties) {
        var cors = properties.cors();
        return new CorsConfigurer(
                cors.allowedOriginsOrDefault(), cors.allowedMethodsOrDefault(), cors.allowedHeadersOrDefault());
    }

    private static double require(Double value, String property) {
        if (value == null) {
            throw new IllegalStateException("missing required engine configuration: meethalfway.engine." + property);
        }
        return value;
    }

    private static int requireInt(Integer value, String property) {
        if (value == null) {
            throw new IllegalStateException("missing required engine configuration: meethalfway.engine." + property);
        }
        return value;
    }
}
