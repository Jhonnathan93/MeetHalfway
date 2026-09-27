/**
 * Config layer — externalized configuration binding and explicit Dependency
 * Injection wiring.
 *
 * <p>This is the composition root: Spring {@code @Configuration} classes wire
 * domain and application collaborators together using <em>constructor injection
 * only</em>. Field injection is prohibited across the codebase. City-specific
 * values (service bounds, grid density, search radius, outlier rule, epsilon) are
 * bound here from external configuration and passed inward as {@code EngineConfig},
 * so no city-specific value is embedded in the optimization logic.
 *
 * <p>Requirements: 5.4, 11.5.
 */
package app.meethalfway.config;
