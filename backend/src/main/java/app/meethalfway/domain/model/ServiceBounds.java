package app.meethalfway.domain.model;

/**
 * A latitude/longitude bounding box describing the served region (Requirement
 * 1.7). Participant locations that fall outside these bounds are rejected by the
 * engine; the box itself is city-specific and therefore lives in configuration
 * ({@link EngineConfig}) rather than being embedded in the optimization logic
 * (Requirements 5.4, 11.5).
 *
 * <p>Invariants enforced at construction so an invalid box cannot exist:
 * <ul>
 *   <li>every bound is a finite number;</li>
 *   <li>each latitude lies in {@code [-90, 90]} and each longitude in
 *       {@code [-180, 180]} (the same universal ranges as {@link Coordinate});</li>
 *   <li>{@code minLat <= maxLat} and {@code minLng <= maxLng}.</li>
 * </ul>
 *
 * @param minLat southern latitude bound, within {@code [-90, 90]}
 * @param maxLat northern latitude bound, within {@code [-90, 90]}
 * @param minLng western longitude bound, within {@code [-180, 180]}
 * @param maxLng eastern longitude bound, within {@code [-180, 180]}
 */
public record ServiceBounds(double minLat, double maxLat, double minLng, double maxLng) {

    private static final double MIN_LAT = -90.0;
    private static final double MAX_LAT = 90.0;
    private static final double MIN_LNG = -180.0;
    private static final double MAX_LNG = 180.0;

    public ServiceBounds {
        requireFinite(minLat, "minLat");
        requireFinite(maxLat, "maxLat");
        requireFinite(minLng, "minLng");
        requireFinite(maxLng, "maxLng");
        requireInRange(minLat, MIN_LAT, MAX_LAT, "minLat");
        requireInRange(maxLat, MIN_LAT, MAX_LAT, "maxLat");
        requireInRange(minLng, MIN_LNG, MAX_LNG, "minLng");
        requireInRange(maxLng, MIN_LNG, MAX_LNG, "maxLng");
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
     * Tests whether a coordinate lies within this bounding box (inclusive).
     *
     * @param coordinate the coordinate to test; must not be null
     * @return {@code true} when the coordinate is within the box, inclusive of edges
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

    private static void requireInRange(double value, double min, double max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    name + " must be within [" + min + ", " + max + "], was: " + value);
        }
    }
}
