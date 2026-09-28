package app.meethalfway.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.locations.web.LocationController;
import app.meethalfway.locations.web.dto.AddressSuggestionResponse;
import app.meethalfway.locations.web.dto.LocationMapper;
import app.meethalfway.meetings.application.ComputeRecommendations;
import app.meethalfway.meetings.application.CreateMeeting;
import app.meethalfway.meetings.application.DeleteMeeting;
import app.meethalfway.meetings.application.EditMeeting;
import app.meethalfway.meetings.application.GetMeeting;
import app.meethalfway.meetings.application.UrlCodeGenerator;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.OutlierRule;
import app.meethalfway.meetings.domain.model.ServiceBounds;
import app.meethalfway.meetings.web.MeetingController;
import app.meethalfway.meetings.web.dto.CoordinateWarningResponse;
import app.meethalfway.meetings.web.dto.CreateMeetingRequest;
import app.meethalfway.meetings.web.dto.EditMeetingRequest;
import app.meethalfway.meetings.web.dto.MeetingResponse;
import app.meethalfway.meetings.web.dto.OutlierTradeoffResponse;
import app.meethalfway.meetings.web.dto.ParticipantRequest;
import app.meethalfway.meetings.web.dto.ParticipantResponse;
import app.meethalfway.meetings.web.dto.RecommendationResponse;
import app.meethalfway.meetings.web.dto.RoutingErrorResponse;
import app.meethalfway.meetings.web.dto.RoutingFailureResponse;
import app.meethalfway.meetings.web.dto.StrategyResultResponse;
import app.meethalfway.meetings.web.dto.StrategyResultsResponse;
import app.meethalfway.meetings.web.dto.WebMapper;
import app.meethalfway.meetings.web.support.NoOpRoutingProvider;
import app.meethalfway.meetings.web.support.StubRecommendationEngine;
import app.meethalfway.meetings.web.support.TestMeetingRepository;
import app.meethalfway.shared.testing.FakeGeocodingProvider;
import app.meethalfway.shared.web.dto.CoordinateResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * OpenAPI contract test (Task 12.1; Requirement 7.7).
 *
 * <p>The backend toolchain now supports OpenAPI generation via
 * {@code springdoc-openapi-starter-webmvc-ui}, which serves the description at
 * {@code /v3/api-docs}. Requirement 7.7 requires that the documented paths, DTO
 * schemas, and field names match the exposed endpoints and DTO records. This
 * test asserts exactly that, additively and without changing any endpoint,
 * method, DTO field, or behavior.
 *
 * <p><strong>Why not {@code @SpringBootTest} + {@code MockMvc GET /v3/api-docs}?</strong>
 * Springdoc's full auto-configuration only publishes the document from a running
 * web application context, which triggers Spring's component scanning. As the
 * pom and the sibling web-adapter tests document, Spring's bundled ASM cannot
 * reliably scan JDK 25 bytecode (class-file major version 69) in a full MVC test
 * context in this environment, and the {@code final} use cases cannot be mocked
 * under JDK 25's Byte Buddy. Every web-adapter test here therefore uses the
 * offline standalone approach. This test follows the same discipline and asserts
 * the two halves of the documented contract through the exact seams springdoc
 * itself uses:
 *
 * <ol>
 *   <li><strong>Documented paths</strong> are derived from the same
 *       {@link RequestMappingHandlerMapping} that both Spring MVC and springdoc's
 *       {@code OpenApiResource} walk to enumerate operations. Asserting the wired
 *       (method, path) pairs equals asserting the paths springdoc documents,
 *       because springdoc reads them from this very handler mapping.</li>
 *   <li><strong>DTO schemas and field names</strong> are generated with
 *       swagger-core's {@link ModelConverters} — the identical schema engine
 *       springdoc delegates to when it renders {@code components/schemas}. The
 *       generated schema property names are asserted against the actual record
 *       components, so the documented field names cannot drift from the records.</li>
 * </ol>
 *
 * <p>This keeps the test fully offline and reliable under JDK 25 while still
 * exercising springdoc's real generation machinery (the handler mapping + swagger
 * schema converters), rather than a hand-rolled stand-in.
 */
class OpenApiContractTest {

    private RequestMappingHandlerMapping handlerMapping;

    /** A wired route: an exposed HTTP method + path pattern pair. */
    private record Route(RequestMethod method, String pattern) {}

    @BeforeEach
    void setUp() {
        EngineConfig engineConfig = new EngineConfig(
                new ServiceBounds(-90.0, 90.0, -180.0, 180.0),
                100, 20000.0, new OutlierRule.MedianMultiple(2.0), 0.5);

        TestMeetingRepository repository = new TestMeetingRepository();
        MeetingController meetingController = new MeetingController(
                new CreateMeeting(repository, new UrlCodeGenerator()),
                new GetMeeting(repository),
                new EditMeeting(repository),
                new DeleteMeeting(repository),
                new ComputeRecommendations(
                        new StubRecommendationEngine(), repository, new NoOpRoutingProvider(), engineConfig),
                new WebMapper());
        LocationController locationController = new LocationController(
                FakeGeocodingProvider.builder().build(),
                engineConfig,
                new LocationMapper());
        HealthController healthController = new HealthController();

        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("meetingController", meetingController);
        context.getBeanFactory().registerSingleton("locationController", locationController);
        context.getBeanFactory().registerSingleton("healthController", healthController);
        context.refresh();

        handlerMapping = new RequestMappingHandlerMapping();
        handlerMapping.setApplicationContext(context);
        handlerMapping.afterPropertiesSet();
    }

    private Set<Route> wiredRoutes() {
        Set<Route> routes = new LinkedHashSet<>();
        for (Map.Entry<RequestMappingInfo, ?> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            Set<String> patterns = info.getPathPatternsCondition() != null
                    ? info.getPathPatternsCondition().getPatternValues()
                    : info.getPatternsCondition().getPatterns();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : patterns) {
                for (RequestMethod method : methods) {
                    routes.add(new Route(method, pattern));
                }
            }
        }
        return routes;
    }

    // ---------------------------------------------------------------------
    // Documented paths match the exposed endpoints
    // ---------------------------------------------------------------------

    @Test
    void documentedPathsMatchExposedEndpointsAndStayUnderApiV1() {
        Set<Route> wired = wiredRoutes();

        List<Route> expected = List.of(
                // Meetings CRUD
                new Route(RequestMethod.POST, "/api/v1/meetings"),
                new Route(RequestMethod.GET, "/api/v1/meetings/{code}"),
                new Route(RequestMethod.PUT, "/api/v1/meetings/{code}"),
                new Route(RequestMethod.DELETE, "/api/v1/meetings/{code}"),
                // Recommendations
                new Route(RequestMethod.POST, "/api/v1/meetings/{code}/recommendations"),
                // Geocode autocomplete/resolve
                new Route(RequestMethod.GET, "/api/v1/geocode/autocomplete"),
                new Route(RequestMethod.GET, "/api/v1/geocode/resolve"),
                // Health
                new Route(RequestMethod.GET, "/api/v1/health"));

        assertThat(wired)
                .as("the documented OpenAPI paths (read from the same handler mapping "
                        + "springdoc walks) must match every exposed endpoint")
                .containsAll(expected);

        // Every documented API route stays under /api/v1 (Requirement 7.7 / R6.5).
        // Springdoc may additionally expose its own /v3/api-docs and Swagger UI
        // resources; those are documentation infrastructure, not application API
        // routes, and are excluded from this application-surface assertion.
        assertThat(wired.stream().map(Route::pattern))
                .allSatisfy(pattern -> assertThat(pattern).startsWith("/api/v1"));
    }

    @Test
    void everyRegistryBasePathIsDocumented() {
        Set<String> wiredPatterns =
                wiredRoutes().stream().map(Route::pattern).collect(Collectors.toSet());

        for (RouteRegistry module : RouteRegistry.all()) {
            String basePath = module.basePath();
            boolean documented = wiredPatterns.stream()
                    .anyMatch(p -> p.equals(basePath) || p.startsWith(basePath + "/"));
            assertThat(documented)
                    .as("RouteRegistry module %s (base path %s) must be documented; wired: %s",
                            module.name(), basePath, wiredPatterns)
                    .isTrue();
        }
    }

    // ---------------------------------------------------------------------
    // Documented DTO schemas and field names match the DTO records
    // ---------------------------------------------------------------------

    /**
     * Resolves the swagger schema property names for a type using the same
     * {@link ModelConverters} engine springdoc renders {@code components/schemas}
     * with, then asserts they equal the expected camelCase field set.
     */
    private void assertSchemaFields(Class<?> type, Set<String> expectedFields) {
        Map<String, Schema> schemas = ModelConverters.getInstance().readAll(type);
        Schema<?> schema = schemas.get(type.getSimpleName());
        assertThat(schema)
                .as("springdoc/swagger must generate a component schema for %s", type.getSimpleName())
                .isNotNull();
        assertThat(schema.getProperties())
                .as("schema for %s must expose properties", type.getSimpleName())
                .isNotNull();
        assertThat(schema.getProperties().keySet())
                .as("documented field names for %s must match its record components exactly",
                        type.getSimpleName())
                .containsExactlyInAnyOrderElementsOf(expectedFields);
    }

    @Test
    void requestDtoSchemasMatchRecords() {
        assertSchemaFields(CreateMeetingRequest.class, Set.of("participants", "transportMode"));
        assertSchemaFields(EditMeetingRequest.class, Set.of("participants", "transportMode"));
        assertSchemaFields(ParticipantRequest.class, Set.of("name", "lat", "lng"));
    }

    @Test
    void meetingResponseSchemasMatchRecords() {
        assertSchemaFields(
                MeetingResponse.class,
                Set.of("urlCode", "participants", "transportMode", "recommendation"));
        assertSchemaFields(ParticipantResponse.class, Set.of("id", "name", "location"));
        assertSchemaFields(CoordinateResponse.class, Set.of("lat", "lng"));
    }

    @Test
    void recommendationResponseSchemasMatchRecords() {
        assertSchemaFields(
                RecommendationResponse.class, Set.of("results", "outlierTradeoff", "warnings"));
        assertSchemaFields(
                StrategyResultsResponse.class, Set.of("fastest", "minimax", "fairest"));
        assertSchemaFields(
                StrategyResultResponse.class,
                Set.of("point", "perParticipant", "sumTime", "maxTime", "stdDev",
                        "candidateId", "recommended"));
        assertSchemaFields(
                CoordinateWarningResponse.class,
                Set.of("kind", "reference", "lat", "lng", "reason"));
        assertSchemaFields(
                OutlierTradeoffResponse.class,
                Set.of("outliers", "including", "excluding",
                        "avgTravelTimeIncluding", "avgTravelTimeExcluding"));
    }

    @Test
    void routingFailureAndGeocodeSchemasMatchRecords() {
        assertSchemaFields(
                RoutingFailureResponse.class, Set.of("code", "message", "errors"));
        assertSchemaFields(
                RoutingErrorResponse.class, Set.of("participantId", "location", "reason"));
        assertSchemaFields(
                AddressSuggestionResponse.class, Set.of("description", "placeId"));
    }
}
