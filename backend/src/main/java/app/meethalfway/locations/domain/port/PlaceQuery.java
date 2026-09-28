package app.meethalfway.locations.domain.port;

/**
 * Parameters constraining a nearby-places lookup for a {@link PlacesProvider}.
 *
 * <p>Post-MVP value type (see {@link Place}). Defined now so the port contract is
 * stable; no MVP adapter consumes it.
 *
 * <p>Invariants: {@code radiusMeters} must be positive and {@code maxResults} must
 * be at least one, so a query always describes a bounded, non-empty search.
 *
 * @param radiusMeters search radius around the center point, in meters (&gt; 0)
 * @param maxResults   maximum number of establishments to return (&ge; 1)
 */
public record PlaceQuery(int radiusMeters, int maxResults) {

    public PlaceQuery {
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("radiusMeters must be positive, was: " + radiusMeters);
        }
        if (maxResults < 1) {
            throw new IllegalArgumentException("maxResults must be at least 1, was: " + maxResults);
        }
    }
}
