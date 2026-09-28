package app.meethalfway.shared.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * Startup-guard tests for {@link RouteRegistryVerifier} (Requirements 3.4, 13.4).
 *
 * <p>When two {@link RouteRegistry} modules declare overlapping base paths, the
 * application must refuse to start with an error naming the conflicting modules
 * and their base paths. Rather than bootstrap a full Spring MVC context (whose
 * bundled ASM cannot parse JDK 25 bytecode under Spring 3.3.5, as noted in the
 * sibling {@code CorsConfigurerTest}), these tests drive the verifier through its
 * {@code List<RouteRegistry>} constructor and exercise both the directly-callable
 * {@link RouteRegistryVerifier#verify()} method and the
 * {@link org.springframework.boot.ApplicationRunner#run} startup entry point. The
 * verifier under test is the exact bean wired into the composition root, so
 * failing it is what fails application startup (Requirement 3.4).
 */
class RouteRegistryVerifierTest {

    /**
     * Two modules declaring the <em>same</em> base path is an overlap: startup
     * must fail with an error naming both conflicting modules and the shared base
     * path (Requirement 3.4).
     */
    @Test
    void verifyFailsWhenTwoModulesDeclareOverlappingBasePaths() {
        RouteRegistryVerifier verifier =
                new RouteRegistryVerifier(List.of(RouteRegistry.MEETINGS, RouteRegistry.MEETINGS));

        assertThatThrownBy(verifier::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RouteRegistry.MEETINGS.name())
                .hasMessageContaining(RouteRegistry.MEETINGS.basePath());
    }

    /**
     * The overlap must fail application <em>startup</em>, not merely a direct
     * call: driving the {@link org.springframework.boot.ApplicationRunner} entry
     * point with real application arguments must throw the same naming error
     * (Requirements 3.4, 13.4).
     */
    @Test
    void runFailsStartupOnOverlapNamingConflictingModulesAndBasePaths() {
        RouteRegistryVerifier verifier =
                new RouteRegistryVerifier(List.of(RouteRegistry.LOCATIONS, RouteRegistry.LOCATIONS));

        assertThatThrownBy(() -> verifier.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RouteRegistry.LOCATIONS.name())
                .hasMessageContaining(RouteRegistry.LOCATIONS.basePath());
    }

    /**
     * The real, shipped {@link RouteRegistry} surface has no overlapping base
     * paths, so the default verifier (the bean wired at the composition root) and
     * the explicit full-surface list both start cleanly. This proves the failure
     * tests above rely on an injected overlap, not an incidental one
     * (Requirements 3.4, 13.4).
     */
    @Test
    void verifyPassesForTheRealNonOverlappingRegistry() {
        assertThatCode(() -> new RouteRegistryVerifier().verify()).doesNotThrowAnyException();
        assertThatCode(() -> new RouteRegistryVerifier(RouteRegistry.all()).verify())
                .doesNotThrowAnyException();
        assertThatCode(() -> new RouteRegistryVerifier().run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }
}
