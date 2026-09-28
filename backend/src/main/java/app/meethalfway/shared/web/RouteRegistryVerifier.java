package app.meethalfway.shared.web;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Startup guard that fails application startup with a clear error when two
 * registered {@link RouteRegistry} base paths overlap (Requirement 3.4).
 *
 * <p>Two base paths overlap when they are equal or when one is a path-prefix of
 * the other (for example {@code /api/v1/meetings} and
 * {@code /api/v1/meetings/extra}). Because this runs as an
 * {@link ApplicationRunner} at startup, an accidental overlap can never ship
 * silently: the application refuses to start and the error names the conflicting
 * modules and their base paths.
 *
 * <p>The verifier is wired via constructor injection at the composition root; it
 * verifies the {@link RouteRegistry} entries supplied to it, so the same logic is
 * unit-testable with a custom registry list.
 */
public final class RouteRegistryVerifier implements ApplicationRunner {

    private final List<RouteRegistry> modules;

    /**
     * Verifies the full {@link RouteRegistry} surface.
     */
    public RouteRegistryVerifier() {
        this(RouteRegistry.all());
    }

    /**
     * Verifies the supplied set of modules. Exposed for focused testing.
     *
     * @param modules the modules whose base paths are checked for overlap; must
     *                not be null
     */
    public RouteRegistryVerifier(List<RouteRegistry> modules) {
        if (modules == null) {
            throw new IllegalArgumentException("modules must not be null");
        }
        this.modules = List.copyOf(modules);
    }

    @Override
    public void run(ApplicationArguments args) {
        verify();
    }

    /**
     * Fails fast if any two registered base paths overlap.
     *
     * @throws IllegalStateException naming the conflicting modules and base paths
     */
    public void verify() {
        List<RouteRegistry> ordered = modules;
        for (int i = 0; i < ordered.size(); i++) {
            for (int j = i + 1; j < ordered.size(); j++) {
                RouteRegistry first = ordered.get(i);
                RouteRegistry second = ordered.get(j);
                if (overlaps(first.basePath(), second.basePath())) {
                    throw new IllegalStateException(String.format(
                            "Route_Registry base-path overlap: module %s (%s) overlaps module %s (%s). "
                                    + "Each API module must own a distinct, non-overlapping base path.",
                            first.name(), first.basePath(), second.name(), second.basePath()));
                }
            }
        }
    }

    /**
     * Two base paths overlap when they are equal, or when one is a path-segment
     * prefix of the other. A prefix match requires the boundary to fall on a
     * {@code '/'} so that sibling paths like {@code /api/v1/meetings} and
     * {@code /api/v1/meetings-archive} are correctly treated as distinct.
     */
    private static boolean overlaps(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        return isPrefixPath(a, b) || isPrefixPath(b, a);
    }

    private static boolean isPrefixPath(String prefix, String candidate) {
        return candidate.startsWith(prefix + "/");
    }
}
