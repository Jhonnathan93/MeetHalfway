/**
 * Framework-free engine components of the domain core: candidate generation
 * ({@code Grid_Search}), metric computation, strategy selection, tie-breaking,
 * and outlier detection. These types depend only on domain model value objects
 * and ports, never on Spring, JPA, HTTP, or a concrete provider (Clean
 * Architecture dependency rule; Requirements 5.4, 11.5).
 */
package app.meethalfway.domain.engine;
