package app.meethalfway.locations.domain.port;

import app.meethalfway.shared.domain.Coordinate;

/**
 * The outcome of resolving an address string to a precise location, modelled as
 * a sealed algebraic type so callers handle both the resolved and the
 * unresolved case explicitly (Requirement 9.7).
 *
 * <p>There are exactly two variants:
 * <ul>
 *   <li>{@link Resolved} — the address was geocoded to a valid
 *       {@link Coordinate}.</li>
 *   <li>{@link NotFound} — the address could not be resolved, with a specific,
 *       non-blank reason so the Creator can correct it rather than proceeding
 *       with a silently wrong location.</li>
 * </ul>
 */
public sealed interface GeocodeResult
        permits GeocodeResult.Resolved, GeocodeResult.NotFound {

    /**
     * A successfully resolved address.
     *
     * @param coordinate the non-null resolved location
     */
    record Resolved(Coordinate coordinate) implements GeocodeResult {

        public Resolved {
            if (coordinate == null) {
                throw new IllegalArgumentException("coordinate must not be null");
            }
        }
    }

    /**
     * An address that could not be resolved to a location.
     *
     * @param reason the non-blank reason resolution failed
     */
    record NotFound(String reason) implements GeocodeResult {

        public NotFound {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must be a non-blank explanation");
            }
        }
    }

    /**
     * Convenience factory for a resolved address.
     *
     * @param coordinate the resolved location
     * @return a {@link Resolved} carrying the given coordinate
     */
    static GeocodeResult resolved(Coordinate coordinate) {
        return new Resolved(coordinate);
    }

    /**
     * Convenience factory for an unresolved address.
     *
     * @param reason the non-blank reason resolution failed
     * @return a {@link NotFound} carrying the given reason
     */
    static GeocodeResult notFound(String reason) {
        return new NotFound(reason);
    }
}
