/**
 * Domain layer — the pure core of the Meeting Recommendation Engine.
 *
 * <p><strong>Dependency rule:</strong> the domain depends on <em>nothing</em>. It
 * must not reference the {@code application}, {@code adapters}, or {@code config}
 * layers, and it must stay free of frameworks (no Spring, JPA, HTTP, or provider
 * SDKs). It contains entities, value objects, the engine, and the outbound
 * <em>ports</em> (interfaces) that adapters implement.
 *
 * <p>Requirements: 5.4, 11.5 — city-specific values are never embedded here; they
 * arrive through {@code EngineConfig}, keeping the core city-agnostic.
 */
package app.meethalfway.domain;
