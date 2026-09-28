package app.meethalfway.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

/**
 * CORS behavior tests for {@link CorsConfigurer} (Task 12.4).
 *
 * <p>Rather than bootstrap a Spring MVC context (whose bundled ASM cannot parse
 * JDK 25 bytecode under Spring 3.3.5), this drives the configurer against a real
 * {@link CorsRegistry} and inspects the resulting {@link CorsConfiguration}. It
 * proves that only the configured origins are allowed and that a disallowed
 * origin is rejected &mdash; i.e. the wildcard is never in effect.
 */
class CorsConfigurerTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String DISALLOWED_ORIGIN = "http://evil.example";

    /** Minimal registry that exposes the accumulated CORS configurations. */
    private static final class InspectableRegistry extends CorsRegistry {
        @Override
        protected Map<String, CorsConfiguration> getCorsConfigurations() {
            return super.getCorsConfigurations();
        }
    }

    private Map<String, CorsConfiguration> configure() {
        CorsConfigurer configurer = new CorsConfigurer(
                List.of(ALLOWED_ORIGIN),
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"),
                List.of("Content-Type", "Accept"));
        InspectableRegistry registry = new InspectableRegistry();
        configurer.addCorsMappings(registry);
        return registry.getCorsConfigurations();
    }

    @Test
    void mappingIsScopedToTheVersionedApiPath() {
        assertThat(configure()).containsKey("/api/v1/**");
    }

    @Test
    void allowsTheConfiguredOrigin() {
        CorsConfiguration config = configure().get("/api/v1/**");
        assertThat(config.checkOrigin(ALLOWED_ORIGIN)).isEqualTo(ALLOWED_ORIGIN);
    }

    @Test
    void rejectsADisallowedOrigin() {
        CorsConfiguration config = configure().get("/api/v1/**");
        assertThat(config.checkOrigin(DISALLOWED_ORIGIN)).isNull();
    }

    @Test
    void doesNotUseWildcardOrigin() {
        CorsConfiguration config = configure().get("/api/v1/**");
        assertThat(config.getAllowedOrigins()).containsExactly(ALLOWED_ORIGIN);
        assertThat(config.getAllowedOrigins()).doesNotContain("*");
    }

    @Test
    void constructorRejectsEmptyOrigins() {
        assertThatThrownBy(() -> new CorsConfigurer(List.of(), List.of("GET"), List.of("Accept")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
