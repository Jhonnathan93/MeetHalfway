package app.meethalfway.meetings.domain.engine;

import app.meethalfway.shared.domain.Coordinate;

/**
 * The bounded lat/lng box over which {@code Grid_Search} places its candidate
 * points. The region is derived from the participant origins, expanded by the
 * configured maximum search radius, and clipped to the configured service
 * bounds; see {@link CandidateGenerator#searchRegion}.
 *
 * <p>Invariants enforced at construction so an invalid region cannot exist:
 * <ul>
 *   <li>every bound is finite;</li>
 *   <li>{@code minLat <= maxLat} and {@code minLng <= maxLng} (the box may be
 *       degenerate, i.e. a single point or line, but never inverted).</li>
 * </ul>
 *
 * @param minLat southern latitude bound
 * @param maxLat northern latitude bound
 * @param minLng western longitude bound
 * @param maxLng eastern longitude bound
 */
public record SearchRegion(double minLat, double maxLat, double minLng, double maxLng) {

    public SearchRegion {
        requireFinite(minLat, "minLat");
        requireFinite(maxLat, "maxLat");
        requireFinite(minLng, "minLng");
        requireFinite(maxLng, "maxLng");
        if (minLat > maxLat) {
            throw new IllegalArgumentException(
                    "minLat must be <= maxLat, was: minLat=" + minLat + ", maxLat=" + maxLat);
        }
        if (minLng > maxLng) {
            throw new IllegalArgumentException(
                    "minLng must be <= maxLng, was: minLng=" + minLng + ", maxLng=" + maxLng);
        }
    }

    /**
     * Tests whether a coordinate lies within this region (inclusive of edges).
     *
     * @param coordinate the coordinate to test; must not be null
     * @return {@code true} when the coordinate is inside the box, edges included
     */
    public boolean contains(Coordinate coordinate) {
        if (coordinate == null) {
            throw new IllegalArgumentException("coordinate must not be null");
        }
        return coordinate.lat() >= minLat
                && coordinate.lat() <= maxLat
                && coordinate.lng() >= minLng
                && coordinate.lng() <= maxLng;
    }

    private static void requireFinite(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite number, was: " + value);
        }
    }
}
