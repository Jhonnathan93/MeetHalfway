package app.meethalfway.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Mechanically enforces the Clean Architecture inward dependency rule and the
 * constructor-injection-only rule (Requirements 5.4, 11.5, and the design's
 * "Dependency Injection wiring" note).
 *
 * <p>These are plain JUnit 5 tests over imported bytecode; they do not start a
 * Spring context, so the build/verify step needs no database.
 */
class ArchitectureRulesTest {

    private static final String BASE = "app.meethalfway";

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    /**
     * Domain depends on nothing (inner-most); Application depends on Domain;
     * Adapters and Config sit at the edges and may depend on inner layers.
     */
    @Test
    void layerDependenciesPointInward() {
        ArchRule rule = layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy(BASE + ".domain..")
                .layer("Application").definedBy(BASE + ".application..")
                .layer("Adapters").definedBy(BASE + ".adapters..")
                .layer("Config").definedBy(BASE + ".config..")
                // Domain is the core: nothing inward, so only outer layers may use it.
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapters", "Config")
                // Application may only be reached from the edges.
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapters", "Config")
                // Adapters are an edge, but Config is the composition root that
                // wires them, so Adapters may be accessed only by Config; nothing
                // inner (Domain, Application) may depend on them — that is the
                // invariant that matters for the dependency rule.
                .whereLayer("Adapters").mayOnlyBeAccessedByLayers("Config")
                // Config holds the externalized settings records that adapters
                // bind to (e.g. RoutingProperties), so Adapters may access Config;
                // and Config is the composition root, accessed by no inner layer.
                // Listing only Adapters keeps Domain and Application forbidden
                // from touching Config, preserving the inward dependency rule.
                .whereLayer("Config").mayOnlyBeAccessedByLayers("Adapters");

        rule.check(classes);
    }

    /** Domain must remain framework-free (no Spring / JPA / HTTP leakage). */
    @Test
    void domainIsFrameworkFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.servlet..",
                        "org.hibernate..");

        rule.check(classes);
    }

    /** Application must not know about adapters; it talks through domain ports. */
    @Test
    void applicationDoesNotDependOnAdapters() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE + ".application..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".adapters..");

        rule.check(classes);
    }

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
