/**
 * Application layer — use cases / orchestration.
 *
 * <p><strong>Dependency rule:</strong> the application layer depends only on the
 * {@code domain} layer. It must not depend on {@code adapters} (it talks to the
 * outside world exclusively through domain ports) and it must not depend on
 * {@code config}. Use cases here drive the domain engine and the outbound ports.
 *
 * <p>All collaborators are supplied via <em>constructor injection</em>; field
 * injection is prohibited (see the DI wiring points in the {@code config} layer).
 */
package app.meethalfway.application;
