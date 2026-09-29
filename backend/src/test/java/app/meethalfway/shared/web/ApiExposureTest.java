package app.meethalfway.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.meetings.web.support.NoOpRoutingProvider;
import app.meethalfway.meetings.web.support.TestMeetingRepository;
import app.meethalfway.meetings.domain.engine.GridCandidateGenerator;
import app.meethalfway.meetings.domain.engine.MeetingValidator;
import app.meethalfway.meetings.domain.engine.MetricCalculator;
import app.meethalfway.meetings.domain.engine.OutlierDetector;
import app.meethalfway.meetings.domain.engine.RecommendationEngine;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.OutlierRule;
import app.meethalfway.meetings.domain.model.ServiceBounds;
import app.meethalfway.shared.testing.FakeGeocodingProvider;
import app.meethalfway.locations.web.LocationController;
import app.meethalfway.locations.web.dto.LocationMapper;
import app.meethalfway.meetings.application.RecommendationService;
import app.meethalfway.meetings.application.MeetingService;
import app.meethalfway.meetings.application.UrlCodeGenerator;
import app.meethalfway.meetings.web.MeetingController;
import app.meethalfway.meetings.web.dto.WebMapper;

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
 * API exposure integration test (Requirements 13.4,
 * 6.6).
 *
 * <p>Verifies two things by inspecting Spring MVC's own
 * {@link RequestMappingHandlerMapping} &mdash; the same component that resolves
 * incoming requests to handler methods at runtime &mdash; rather than issuing
 * live requests:
 *
 * <ol>
 *   <li>Every existing external path + HTTP method is preserved: meetings CRUD,
 *       recommendations, geocode autocomplete/resolve, and health (Requirement
 *       6.6, 13.4).</li>
 * </ol>
 *
 * <p>The controllers are registered explicitly into a
 * {@link RequestMappingHandlerMapping} (no component scanning, no live database,
 * no HTTP round-trip). This mirrors the offline standalone-MockMvc approach used
 * by the other web-adapter tests, which is required because Spring's ASM cannot
 * scan JDK 25 bytecode and the {@code final} use cases cannot be mocked under
 * JDK 25's Byte Buddy; here the real use cases are wired to in-memory fakes.
 *
 * <p>The test asserts against the routes as they are <em>currently</em> wired:
 * the geocoding endpoints now live on {@link LocationController} (split out of
 * {@link MeetingController} in task 4.1). It checks path + method presence, so it
 * stays green regardless of which controller class owns a given geocoding path.
 */
class ApiExposureTest {

    private RequestMappingHandlerMapping handlerMapping;

    /**
     * A wired route: an exposed HTTP method + path pattern pair.
     */
    private record Route(RequestMethod method, String pattern) {}

    @BeforeEach
    void setUp() {
        EngineConfig engineConfig = new EngineConfig(
                new ServiceBounds(-90.0, 90.0, -180.0, 180.0),
                100, 20000.0, new OutlierRule(2.0), 0.5);

        TestMeetingRepository repository = new TestMeetingRepository();
        MeetingController meetingController = new MeetingController(
                new MeetingService(repository, new UrlCodeGenerator()),
                new RecommendationService(
                        new RecommendationEngine(new MeetingValidator(), new GridCandidateGenerator(),
                                new MetricCalculator(), new OutlierDetector()),
                        repository, new NoOpRoutingProvider(), engineConfig),
                new WebMapper());
        LocationController locationController = new LocationController(
                FakeGeocodingProvider.builder().build(),
                engineConfig,
                new LocationMapper());
        HealthController healthController = new HealthController();

        // Register the controllers as beans, then build the handler mapping over
        // that context. This wires @RequestMapping metadata exactly as it would
        // be at runtime, without component scanning or a live database.
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("meetingController", meetingController);
        context.getBeanFactory().registerSingleton("locationController", locationController);
        context.getBeanFactory().registerSingleton("healthController", healthController);
        context.refresh();

        handlerMapping = new RequestMappingHandlerMapping();
        handlerMapping.setApplicationContext(context);
        handlerMapping.afterPropertiesSet();
    }

    /**
     * Snapshot of every (method, pattern) pair currently wired to a controller
     * handler method.
     */
    private Set<Route> wiredRoutes() {
        Set<Route> routes = new LinkedHashSet<>();
        for (Map.Entry<RequestMappingInfo, ?> entry :
                handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            Set<String> patterns = info.getPathPatternsCondition() != null
                    ? info.getPathPatternsCondition().getPatternValues()
                    : info.getPatternsCondition().getPatterns();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            // A handler with no explicit method condition (e.g. @GetMapping still
            // records GET) is expected to always carry a method here.
            for (String pattern : patterns) {
                for (RequestMethod method : methods) {
                    routes.add(new Route(method, pattern));
                }
            }
        }
        return routes;
    }

    @Test
    void existingExternalPathsAndMethodsArePreserved() {
        Set<Route> wired = wiredRoutes();

        List<Route> expected = List.of(
                // Meetings CRUD
                new Route(RequestMethod.POST, "/api/v1/meetings"),
                new Route(RequestMethod.GET, "/api/v1/meetings/{code}"),
                new Route(RequestMethod.PUT, "/api/v1/meetings/{code}"),
                new Route(RequestMethod.DELETE, "/api/v1/meetings/{code}"),
                // Recommendations
                new Route(RequestMethod.POST, "/api/v1/meetings/{code}/recommendations"),
                // Geocoding (now served by LocationController, split out of
                // MeetingController in task 4.1)
                new Route(RequestMethod.GET, "/api/v1/geocode/autocomplete"),
                new Route(RequestMethod.GET, "/api/v1/geocode/resolve"),
                // Health
                new Route(RequestMethod.GET, "/api/v1/health"));

        assertThat(wired)
                .as("all existing external path+method pairs must remain wired after the refactor")
                .containsAll(expected);
    }

}
