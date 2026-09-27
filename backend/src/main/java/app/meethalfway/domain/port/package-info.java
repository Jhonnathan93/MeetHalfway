/**
 * Domain ports — the interfaces through which the core talks to the outside world.
 *
 * <p>Following Clean Architecture, these ports are declared in the {@code domain}
 * layer and <em>implemented</em> by outbound adapters (routing, geocoding, places,
 * persistence). The core depends only on these interfaces, never on their concrete
 * implementations, so a provider can be replaced without touching domain or
 * application code.
 *
 * <p>All ports here are framework-free: plain Java interfaces and value types with
 * no Spring, JPA, or HTTP dependencies.
 */
package app.meethalfway.domain.port;
