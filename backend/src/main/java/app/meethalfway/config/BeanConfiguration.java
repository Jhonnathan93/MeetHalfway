package app.meethalfway.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import app.meethalfway.routing.adapters.routing.OsrmRoutingAdapter;
import app.meethalfway.meetings.domain.engine.CandidateGenerator;
import app.meethalfway.meetings.domain.engine.ConfiguredOutlierDetector;
import app.meethalfway.meetings.domain.engine.DefaultRecommendationEngine;
import app.meethalfway.meetings.domain.engine.GridCandidateGenerator;
import app.meethalfway.meetings.domain.engine.MeetingValidator;
import app.meethalfway.meetings.domain.engine.MetricCalculator;
import app.meethalfway.meetings.domain.engine.OutlierDetector;
import app.meethalfway.meetings.domain.engine.RecommendationEngine;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.OutlierRule;
import app.meethalfway.meetings.domain.model.ServiceBounds;
import app.meethalfway.meetings.domain.model.MeetingRepository;
import app.meethalfway.routing.domain.port.RoutingProvider;
import app.meethalfway.locations.adapters.geocoding.GeocodingAdapter;
import app.meethalfway.locations.adapters.geocoding.HttpExchange;
import app.meethalfway.locations.adapters.geocoding.JdkHttpExchange;
import app.meethalfway.locations.domain.port.GeocodingProvider;
import app.meethalfway.locations.web.dto.LocationMapper;
import app.meethalfway.meetings.adapters.persistence.JpaMeetingRepository;
import app.meethalfway.meetings.adapters.persistence.MeetingMapper;
import app.meethalfway.meetings.adapters.persistence.SpringDataMeetingRepository;
import app.meethalfway.meetings.application.ComputeRecommendations;
import app.meethalfway.meetings.application.CreateMeeting;
import app.meethalfway.meetings.application.DeleteMeeting;
import app.meethalfway.meetings.application.EditMeeting;
import app.meethalfway.meetings.application.GetMeeting;
import app.meethalfway.meetings.application.UrlCodeGenerator;
import app.meethalfway.meetings.web.dto.WebMapper;
import app.meethalfway.shared.web.CorsConfigurer;
import app.meethalfway.shared.web.RateLimitFilter;
import app.meethalfway.shared.web.RouteRegistryVerifier;

/**
 * Composition root for explicit Dependency Injection.
 *
 * <p>Domain and application collaborators are wired here as {@code @Bean}
 * definitions using <strong>constructor injection only</strong>. This keeps the
 * pure {@code domain} layer free of any framework annotations: the domain defines
 * plain constructors, and this class supplies their dependencies.
 *
 * <p>Field injection ({@code @Autowired} on fields) is prohibited across the
 * codebase; the {@code ArchitectureRulesTest} enforces this mechanically. As the
 * engine, use cases, and adapters are implemented in later tasks, their beans are
 * declared here so every dependency is passed through a constructor.
 *
 * <p>Example wiring point (declared once the collaborators exist):
 * <pre>{@code
 * @Bean
 * ComputeRecommendations computeRecommendations(RecommendationEngine engine,
 *                                                MeetingRepository repository) {
 *     return new ComputeRecommendations(engine, repository); // constructor injection
 * }
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        EngineConfigProperties.class,
        RoutingProperties.class,
        GeocodingProperties.class,
        RateLimitProperties.class,
        CorsProperties.class})
public class BeanConfiguration {
    // Bean definitions are added as domain/application/adapter collaborators
    // are implemented in subsequent tasks. All wiring uses constructor injection.

    /**
     * Wires the OSRM-backed {@link RoutingProvider} adapter via constructor
     * injection (ADR-001). The routing settings and the Spring-managed JSON
     * mapper are passed explicitly; any configured API key stays backend-only.
     *
     * @param routingProperties the externalized routing configuration
     * @param objectMapper      the shared JSON mapper for parsing responses
     * @return the routing provider used by the recommendation engine
     */
    @Bean
    RoutingProvider routingProvider(RoutingProperties routingProperties, ObjectMapper objectMapper) {
        return new OsrmRoutingAdapter(routingProperties, objectMapper);
    }

    /**
     * Wires the Nominatim-backed {@link GeocodingProvider} adapter via
     * constructor injection (Requirements 9.7, 11.4). The JDK {@link HttpClient}
     * transport, JSON mapper, and backend-only geocoding settings are supplied
     * explicitly; any configured API key stays backend-only and is never
     * exposed to the frontend.
     *
     * @param geocodingProperties the externalized geocoding configuration
     * @param objectMapper        the shared JSON mapper for parsing responses
     * @return the geocoding provider used to power address autocomplete/resolve
     */
    @Bean
    GeocodingProvider geocodingProvider(GeocodingProperties geocodingProperties, ObjectMapper objectMapper) {
        long timeoutMillis = geocodingProperties.timeoutMillis() != null
                ? geocodingProperties.timeoutMillis()
                : 5000L;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMillis))
                .build();
        HttpExchange httpExchange = new JdkHttpExchange(httpClient);
        return new GeocodingAdapter(httpExchange, objectMapper, geocodingProperties);
    }

    // ---- Persistence adapter (Task 9.2) ---------------------------------

    /** Clock for testable creation/update timestamps; UTC to match the DB. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Stateless entity/domain mapper for the persistence adapter. */
    @Bean
    MeetingMapper meetingMapper() {
        return new MeetingMapper();
    }

    /**
     * Wires the domain {@link MeetingRepository} port to its JPA implementation
     * via constructor injection. The Spring Data repository, mapper, and clock
     * are passed explicitly so the domain never sees a persistence type.
     */
    @Bean
    MeetingRepository meetingRepository(
            SpringDataMeetingRepository springDataMeetingRepository,
            MeetingMapper meetingMapper,
            Clock clock) {
        return new JpaMeetingRepository(springDataMeetingRepository, meetingMapper, clock);
    }

    /** URL code generator used by {@link CreateMeeting}. */
    @Bean
    UrlCodeGenerator urlCodeGenerator() {
        return new UrlCodeGenerator();
    }

    // ---- Recommendation engine (domain) ---------------------------------

    @Bean
    MeetingValidator meetingValidator() {
        return new MeetingValidator();
    }

    @Bean
    CandidateGenerator candidateGenerator() {
        return new GridCandidateGenerator();
    }

    @Bean
    MetricCalculator metricCalculator() {
        return new MetricCalculator();
    }

    @Bean
    OutlierDetector outlierDetector() {
        return new ConfiguredOutlierDetector();
    }

    /**
     * Assembles the {@link RecommendationEngine} from its pure collaborators via
     * constructor injection (ADR / design "DI wiring" note).
     */
    @Bean
    RecommendationEngine recommendationEngine(
            MeetingValidator meetingValidator,
            CandidateGenerator candidateGenerator,
            MetricCalculator metricCalculator,
            OutlierDetector outlierDetector) {
        return new DefaultRecommendationEngine(
                meetingValidator, candidateGenerator, metricCalculator, outlierDetector);
    }

    /**
     * Maps the framework-facing {@link EngineConfigProperties} into the
     * framework-free domain {@link EngineConfig} at the composition root. The
     * 15% efficiency tolerance is deliberately not mapped: it is a fixed domain
     * constant (Requirement 4.3), never a config value. The outlier rule defaults
     * to a median-multiple of {@code k = 2.0} when unset, pending calibration.
     *
     * @param properties the bound engine settings; required fields must be present
     * @return the domain engine configuration snapshot
     */
    @Bean
    EngineConfig engineConfig(EngineConfigProperties properties) {
        ServiceBounds serviceBounds = new ServiceBounds(
                require(properties.serviceBoundsMinLat(), "service-bounds-min-lat"),
                require(properties.serviceBoundsMaxLat(), "service-bounds-max-lat"),
                require(properties.serviceBoundsMinLng(), "service-bounds-min-lng"),
                require(properties.serviceBoundsMaxLng(), "service-bounds-max-lng"));
        int gridDensityN = requireInt(properties.gridDensityN(), "grid-density-n");
        double maxSearchRadiusMeters =
                require(properties.maxSearchRadiusMeters(), "max-search-radius-meters");
        double epsilonMinutes = require(properties.epsilonMinutes(), "epsilon-minutes");
        double k = properties.outlierMedianMultipleK() != null
                ? properties.outlierMedianMultipleK()
                : 2.0;
        OutlierRule outlierRule = new OutlierRule.MedianMultiple(k);
        return new EngineConfig(
                serviceBounds, gridDensityN, maxSearchRadiusMeters, outlierRule, epsilonMinutes);
    }

    private static double require(Double value, String property) {
        if (value == null) {
            throw new IllegalStateException(
                    "missing required engine configuration: meethalfway.engine." + property);
        }
        return value;
    }

    private static int requireInt(Integer value, String property) {
        if (value == null) {
            throw new IllegalStateException(
                    "missing required engine configuration: meethalfway.engine." + property);
        }
        return value;
    }

    // ---- Application use cases (Tasks 11.1, 11.2) -----------------------

    @Bean
    CreateMeeting createMeeting(MeetingRepository meetingRepository, UrlCodeGenerator urlCodeGenerator) {
        return new CreateMeeting(meetingRepository, urlCodeGenerator);
    }

    @Bean
    GetMeeting getMeeting(MeetingRepository meetingRepository) {
        return new GetMeeting(meetingRepository);
    }

    @Bean
    EditMeeting editMeeting(MeetingRepository meetingRepository) {
        return new EditMeeting(meetingRepository);
    }

    @Bean
    DeleteMeeting deleteMeeting(MeetingRepository meetingRepository) {
        return new DeleteMeeting(meetingRepository);
    }

    @Bean
    ComputeRecommendations computeRecommendations(
            RecommendationEngine recommendationEngine,
            MeetingRepository meetingRepository,
            RoutingProvider routingProvider,
            EngineConfig engineConfig) {
        return new ComputeRecommendations(
                recommendationEngine, meetingRepository, routingProvider, engineConfig);
    }

    // ---- Web adapter cross-cutting wiring (Tasks 12.1–12.3) -------------

    /**
     * Startup guard that fails application startup with a clear error when two
     * {@code RouteRegistry} base paths overlap (Requirement 3.4). Wired via
     * constructor injection; it runs at startup as an {@code ApplicationRunner}
     * so an accidental overlap can never ship silently.
     *
     * @return the route-registry overlap verifier
     */
    @Bean
    RouteRegistryVerifier routeRegistryVerifier() {
        return new RouteRegistryVerifier();
    }

    /**
     * Stateless mapper between web DTOs, application inputs, and domain types.
     * Shared as a singleton; it holds no state.
     */
    @Bean
    WebMapper webMapper() {
        return new WebMapper();
    }

    /**
     * Stateless mapper for the locations module's geocoding responses. Shared as
     * a singleton; it holds no state. Injected into {@code LocationController}.
     */
    @Bean
    LocationMapper locationMapper() {
        return new LocationMapper();
    }

    /**
     * Registers the hand-rolled {@link RateLimitFilter} scoped to the versioned
     * API path ({@code /api/v1/*}). The limiter's <em>derived</em> settings are
     * read from {@link RateLimitProperties} here at the composition root and
     * passed as plain primitives, so the web-adapter filter never depends on the
     * config layer.
     *
     * @param rateLimitProperties the externalized rate-limit settings
     * @param objectMapper        the shared JSON mapper for the 429 body
     * @return the registration binding the filter to {@code /api/v1/*}
     */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitProperties rateLimitProperties, ObjectMapper objectMapper) {
        RateLimitFilter filter = new RateLimitFilter(
                rateLimitProperties.enabledOrDefault(),
                rateLimitProperties.requestsPerWindowOrDefault(),
                rateLimitProperties.windowSecondsOrDefault(),
                objectMapper);
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/*");
        registration.setName("rateLimitFilter");
        return registration;
    }

    /**
     * Wires the {@link CorsConfigurer} from the derived {@link CorsProperties}
     * values (Requirement 11.4). The web adapter receives plain lists, never the
     * config record, preserving the layered dependency rule.
     *
     * @param corsProperties the externalized CORS settings
     * @return the CORS configurer scoped to {@code /api/v1/**}
     */
    @Bean
    CorsConfigurer corsConfigurer(CorsProperties corsProperties) {
        return new CorsConfigurer(
                corsProperties.allowedOriginsOrDefault(),
                corsProperties.allowedMethodsOrDefault(),
                corsProperties.allowedHeadersOrDefault());
    }
}
