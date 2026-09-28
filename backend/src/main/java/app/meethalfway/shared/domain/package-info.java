/**
 * Shared cross-module domain primitives.
 *
 * <p>Destination for the single, canonical definitions of cross-module value
 * types ({@code Coordinate}, {@code TransportMode}, {@code Minutes},
 * {@code ParticipantId}) referenced by the {@code meetings}, {@code locations},
 * and {@code routing} modules. Populated in a later migration step; classes are
 * relocated here without changing their public signatures or behavior.
 */
package app.meethalfway.shared.domain;
