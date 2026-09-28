package app.meethalfway.shared.web;

import java.util.List;

/**
 * The single, readable composition point that documents which Domain_Modules
 * expose which API routes under {@code /api/v1} (Requirement 3.1, 3.3).
 *
 * <p>Spring MVC has no central {@code urls.py}: routes are declared per
 * controller via {@code @RequestMapping}/{@code @GetMapping} annotations. Rather
 * than fight that convention with a duplicate routing file (two sources of truth
 * that can drift), this registry enumerates each API-exposing module together
 * with the <em>base path</em> it owns. Controllers reference these constants in
 * their {@code @RequestMapping} so the registry and the annotations share one
 * source of truth for base paths (Requirement 3.2). Each module keeps ownership
 * of its concrete path segments and HTTP methods within its own package.
 *
 * <p>All base paths remain under {@code /api/v1} using kebab-case segments
 * (Requirement 3.3). Adding a new API module appends one entry here and its own
 * controller; no unrelated module's routes are edited (Requirement 3.5).
 *
 * <p>{@link RouteRegistryVerifier} checks at application startup that no two
 * declared base paths overlap (Requirement 3.4).
 */
public enum RouteRegistry {

    /** Meetings CRUD and recommendations. Owns {@code /api/v1/meetings}. */
    MEETINGS("/api/v1/meetings"),

    /** Geocoding autocomplete and resolve. Owns {@code /api/v1/geocode}. */
    LOCATIONS("/api/v1/geocode"),

    /** Liveness/health surface. Owns {@code /api/v1/health}. */
    HEALTH("/api/v1/health");

    /** Constant form of {@link #MEETINGS} base path for use in annotations. */
    public static final String MEETINGS_BASE_PATH = "/api/v1/meetings";

    /** Constant form of {@link #LOCATIONS} base path for use in annotations. */
    public static final String LOCATIONS_BASE_PATH = "/api/v1/geocode";

    /** Constant form of {@link #HEALTH} base path for use in annotations. */
    public static final String HEALTH_BASE_PATH = "/api/v1/health";

    private final String basePath;

    RouteRegistry(String basePath) {
        this.basePath = basePath;
    }

    /**
     * @return the base path this module owns, e.g. {@code /api/v1/meetings}
     */
    public String basePath() {
        return basePath;
    }

    /**
     * @return every registered module, in declaration order (a readable
     *         snapshot of the exposed API surface)
     */
    public static List<RouteRegistry> all() {
        return List.of(values());
    }
}
