package app.meethalfway.config;

import app.meethalfway.adapters.geocoding.GeocodingAdapter;
import app.meethalfway.adapters.geocoding.HttpExchange;
import app.meethalfway.adapters.geocoding.JdkHttpExchange;
import app.meethalfway.adapters.routing.OsrmRoutingAdapter;
import app.meethalfway.domain.port.GeocodingProvider;
import app.meethalfway.domain.port.RoutingProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
@EnableConfigurationProperties({EngineConfigProperties.class, RoutingProperties.class, GeocodingProperties.class})
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
}
