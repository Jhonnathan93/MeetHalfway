package app.meethalfway.domain.engine;

import app.meethalfway.domain.model.Coordinate;
import java.util.List;

/**
 * Computes the {@code Geographic_Centroid} of a set of participant locations —
 * the arithmetic mean of their latitudes and longitudes.
 *
 * <p>The centroid is used for a single, deliberately narrow purpose: it is the
 * <em>final</em> tie-breaker in every strategy's comparator chain (Requirements
 * 2.5, 3.4, 4.7). The engine never selects a meeting point <em>by</em> the
 * geographic center (Requirement 5.3); the centroid only breaks a residual tie
 * between candidates that are otherwise indistinguishable on all travel-time
 * metrics, and it does so deterministically.
 *
 * <p>The mean is computed as a plain arithmetic average of the raw
 * latitude/longitude values. This is a planar approximation, which is entirely
 * adequate for a tie-breaker over a bounded metropolitan region and keeps the
 * result deterministic and cheap. Because every input {@link Coordinate} is
 * already range-valid ({@code lat ∈ [-90, 90]}, {@code lng ∈ [-180, 180]}), the
 * mean of those values stays within the same range, so the resulting
 * {@code Coordinate} is always constructible.
 *
 * <p>This is framework-free domain logic with no dependency on HTTP, persistence,
 * or any provider.
 */
public final class GeographicCentroid {

    private GeographicCentroid() {
        // Utility holder for the explicit centroid computation; not instantiable.
    }

    /**
     * Computes the arithmetic-mean centroid of {@code locations}.
     *
     * @param locations the participant locations; must be non-null, non-empty,
     *                  and contain no null element
     * @return the centroid coordinate (mean latitude, mean longitude)
     * @throws IllegalArgumentException if {@code locations} is null, empty, or
     *                                  contains a null element
     */
    public static Coordinate of(List<Coordinate> locations) {
        if (locations == null) {
            throw new IllegalArgumentException("locations must not be null");
        }
        if (locations.isEmpty()) {
            throw new IllegalArgumentException("locations must not be empty");
        }

        double sumLat = 0.0;
        double sumLng = 0.0;
        for (Coordinate location : locations) {
            if (location == null) {
                throw new IllegalArgumentException("locations must not contain a null coordinate");
            }
            sumLat += location.lat();
            sumLng += location.lng();
        }

        int n = locations.size();
        return new Coordinate(sumLat / n, sumLng / n);
    }

    /**
     * Squared planar (Euclidean, degree-space) distance between two coordinates.
     * Squared distance is used because it preserves the same ordering as the true
     * distance while avoiding an unnecessary {@code sqrt}; the tie-breaker only
     * needs the relative order, never the absolute magnitude.
     *
     * @param a first coordinate; must not be null
     * @param b second coordinate; must not be null
     * @return the squared degree-space distance between {@code a} and {@code b}
     * @throws IllegalArgumentException if either argument is null
     */
    public static double squaredDistance(Coordinate a, Coordinate b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("coordinates must not be null");
        }
        double dLat = a.lat() - b.lat();
        double dLng = a.lng() - b.lng();
        return dLat * dLat + dLng * dLng;
    }
}
