/**
 * Domain value objects and enums — immutable, framework-free building blocks of
 * the Meeting Recommendation Engine.
 *
 * <p>Each type re-validates its invariants on construction (Requirements
 * 1.1&ndash;1.4, 1.6, 8.2) so an invalid instance cannot be built, giving the
 * pure core a "cannot be constructed in an invalid state" guarantee independent
 * of any web-adapter validation.
 *
 * <p>Consistent with the {@code domain} dependency rule, this package references
 * no framework: no Spring, JPA, HTTP, or provider SDKs.
 */
package app.meethalfway.domain.model;
