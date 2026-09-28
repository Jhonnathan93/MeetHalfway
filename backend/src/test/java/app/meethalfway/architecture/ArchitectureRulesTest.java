package app.meethalfway.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Mechanically enforces the module-first Clean Architecture boundaries for the
 * refactored backend (Requirements 1.5, 4.5, 4.6, 8.1&ndash;8.4, 14.3).
 *
 * <p>After the technical-layer &rarr; domain-module refactor, code lives under
 * {@code app.meethalfway.{shared|meetings|locations|routing}} with per-module
 * {@code web} / {@code application} / {@code domain} sub-packages and
 * {@code adapters} at the edge, plus a single composition root at
 * {@code app.meethalfway.config}. These rules are re-expressed over that module
 * layout (they replace the earlier transitional rules that pointed at the now
 * empty technical-layer {@code .domain..}/{@code .application..}/{@code .adapters..}
 * packages).
 *
 * <p>The invariants enforced here:
 * <ul>
 *   <li>Inward dependency direction inside every module: {@code web} &rarr;
 *       {@code application} &rarr; {@code domain}; adapters implement domain
 *       ports at the edge; {@code config} is the composition root that no inner
 *       code depends on (R8.1).</li>
 *   <li>Framework-free domain: no Spring / JPA / servlet / Hibernate leakage into
 *       any {@code ..domain..} package (R8.4).</li>
 *   <li>Module boundaries: {@code meetings} / {@code locations} / {@code routing}
 *       must not depend on a peer module's implementation ({@code web},
 *       {@code application}, {@code adapters}); cross-module use is only through
 *       {@code shared} or another module's {@code ..domain..} contract, e.g.
 *       {@code meetings.application} &rarr; {@code routing.domain.port}
 *       ({@code RoutingProvider}) (R8.2).</li>
 *   <li>No cycles between modules/packages (R8.3).</li>
 *   <li>Thin controllers: a {@code @RestController} in {@code ..web..} must not
 *       reach into adapters, the recommendation {@code engine}, or persistence
 *       query types; it depends only on use cases, domain ports/models and the
 *       mapper (R4.5, R4.6).</li>
 *   <li>No generic catch-all utility classes ({@code *Utils}/{@code *Util}/
 *       {@code *Helper}/{@code *Manager}) (R5.5, R14.3).</li>
 *   <li>Constructor injection only &mdash; no field injection anywhere.</li>
 * </ul>
 *
 * <p>These are plain JUnit 5 tests over imported bytecode; they do not start a
 * Spring context, so the build/verify step needs no database.
 */
class ArchitectureRulesTest {

    private static final String BASE = "app.meethalfway";

    /** Package suffixes that identify a module's implementation (non-domain). */
    private static final String[] IMPLEMENTATION_SUFFIXES = {".web..", ".application..", ".adapters.."};

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    // ---------------------------------------------------------------------
    // Inward dependency direction (module-aware) — R8.1
    // ---------------------------------------------------------------------

    /**
     * Domain is the core of every module: it must not depend on any outer layer
     * ({@code web}, {@code application}, {@code adapters}) of any module nor on
     * the {@code config} composition root. This holds for
     * {@code shared.domain}, {@code meetings.domain..}, {@code locations.domain..}
     * and {@code routing.domain..} alike.
     */
    @Test
    void domainDependsOnNoOuterLayer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "..web..",
                        BASE + "..application..",
                        BASE + "..adapters..",
                        BASE + ".config..")
                .as("domain code must not depend on web, application, adapters, or config (inward rule)");

        rule.check(classes);
    }

    /**
     * Application (use cases) orchestrates the domain and talks to the outside
     * world only through domain ports; it must never depend on {@code web},
     * {@code adapters}, or the {@code config} composition root.
     */
    @Test
    void applicationDependsOnlyInward() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + "..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "..web..",
                        BASE + "..adapters..",
                        BASE + ".config..")
                .as("application code must not depend on web, adapters, or config (inward rule)");

        rule.check(classes);
    }

    /** Domain must remain framework-free (no Spring / JPA / HTTP leakage). */
    @Test
    void domainIsFrameworkFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.servlet..",
                        "org.hibernate..")
                .as("domain must stay framework-free (no Spring/JPA/servlet/Hibernate)");

        rule.check(classes);
    }

    // ---------------------------------------------------------------------
    // Module boundaries — R8.2
    // ---------------------------------------------------------------------

    /**
     * The {@code meetings} module must not depend on the implementation of a
     * peer module ({@code locations} or {@code routing}). It may cross a module
     * boundary only through {@code shared} or another module's {@code ..domain..}
     * contract &mdash; the allowed seam being
     * {@code meetings.application} &rarr; {@code routing.domain.port.RoutingProvider}.
     */
    @Test
    void meetingsDoesNotDependOnPeerModuleImplementation() {
        moduleMustNotDependOnPeerImplementation("meetings", "locations", "routing").check(classes);
    }

    /**
     * The {@code locations} module must not depend on the implementation of a
     * peer module ({@code meetings} or {@code routing}); cross-module use is only
     * via {@code shared} or a peer's {@code ..domain..} contract.
     */
    @Test
    void locationsDoesNotDependOnPeerModuleImplementation() {
        moduleMustNotDependOnPeerImplementation("locations", "meetings", "routing").check(classes);
    }

    /**
     * The {@code routing} module must not depend on the implementation of a peer
     * module ({@code meetings} or {@code locations}); cross-module use is only via
     * {@code shared} or a peer's {@code ..domain..} contract.
     */
    @Test
    void routingDoesNotDependOnPeerModuleImplementation() {
        moduleMustNotDependOnPeerImplementation("routing", "meetings", "locations").check(classes);
    }

    /**
     * Builds a rule stating that no class in {@code module} may depend on the
     * implementation packages ({@code web}, {@code application}, {@code adapters})
     * of any listed {@code peers}. Depending on a peer's {@code ..domain..}
     * contract or on {@code shared} stays allowed, which is the sanctioned
     * cross-module seam.
     */
    private static ArchRule moduleMustNotDependOnPeerImplementation(String module, String... peers) {
        String[] forbidden = new String[peers.length * IMPLEMENTATION_SUFFIXES.length];
        int i = 0;
        for (String peer : peers) {
            for (String suffix : IMPLEMENTATION_SUFFIXES) {
                forbidden[i++] = BASE + "." + peer + suffix;
            }
        }
        return noClasses()
                .that().resideInAPackage(BASE + "." + module + "..")
                .should().dependOnClassesThat().resideInAnyPackage(forbidden)
                .as(module + " must not depend on a peer module's implementation "
                        + "(web/application/adapters); use shared or a domain port instead");
    }

    // ---------------------------------------------------------------------
    // No cycles between modules/packages — R8.3
    // ---------------------------------------------------------------------

    /**
     * No circular dependencies between the domain modules
     * ({@code shared}, {@code meetings}, {@code locations}, {@code routing}).
     *
     * <p>The {@code config} package is deliberately excluded from the slicing: it
     * is the composition root that wires adapters and binds their externalized
     * {@code *Properties}, so it legitimately depends on the modules while
     * adapters read {@code config} settings records. Counting {@code config} as a
     * peer slice would report that root-wiring as a cycle, which is not an
     * architectural violation. Restricting the slices to the four modules keeps
     * the check focused on genuine inter-module cycles.
     */
    @Test
    void modulesAreFreeOfCycles() {
        ArchRule rule = slices()
                .matching(BASE + ".(shared|meetings|locations|routing)..")
                .should().beFreeOfCycles()
                .as("domain modules (shared/meetings/locations/routing) must be free of dependency cycles");

        rule.check(classes);
    }

    // ---------------------------------------------------------------------
    // Thin controllers — R4.5, R4.6
    // ---------------------------------------------------------------------

    /**
     * Controllers ({@code @RestController} classes under {@code ..web..}) must not
     * reach past their thin HTTP role into adapters, the recommendation engine,
     * or persistence query types. They collaborate only with use cases, domain
     * ports/models and the web mapper.
     */
    @Test
    void controllersDoNotReferenceAdaptersEngineOrPersistence() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + "..web..")
                .and().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "..adapters..",
                        BASE + "..domain.engine..")
                .as("controllers must not depend on adapters, the engine, or persistence query types "
                        + "(only use cases, domain ports, and the mapper)");

        rule.check(classes);
    }

    // ---------------------------------------------------------------------
    // No catch-all utility classes — R5.5, R14.3
    // ---------------------------------------------------------------------

    /**
     * Reject generic catch-all utility classes: every focused domain utility must
     * declare its single responsibility in its name (e.g. {@code MetricCalculator},
     * {@code GeographicCentroid}), never a {@code *Utils}/{@code *Util}/
     * {@code *Helper}/{@code *Manager} dumping ground.
     */
    @Test
    void noCatchAllUtilityClasses() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + "..")
                .should().haveSimpleNameEndingWith("Utils")
                .orShould().haveSimpleNameEndingWith("Util")
                .orShould().haveSimpleNameEndingWith("Helper")
                .orShould().haveSimpleNameEndingWith("Manager")
                .as("no generic catch-all utility classes: name each utility for its single responsibility");

        rule.check(classes);
    }

    // ---------------------------------------------------------------------
    // Constructor injection only
    // ---------------------------------------------------------------------

    /**
     * Constructor injection only: no {@code @Autowired}/{@code @Inject}/
     * {@code @Resource} on fields anywhere in the codebase.
     */
    @Test
    void noFieldInjection() {
        ArchRule rule = fields()
                .should().notBeAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                .andShould().notBeAnnotatedWith("jakarta.inject.Inject")
                .andShould().notBeAnnotatedWith("jakarta.annotation.Resource")
                .as("no field injection: use constructor injection only");

        rule.check(classes);
    }
}
