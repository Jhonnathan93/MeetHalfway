package app.meethalfway.shared.domain;

/**
 * A geographic point where a participant begins travel.
 *
 * <p>Invariant (Requirement 1.6): latitude must lie in {@code [-90, 90]} and
 * longitude in {@code [-180, 180]}. The compact constructor re-validates these
 * bounds so an out-of-range {@code Coordinate} can never be constructed. Note
 * that <em>service-bounds</em> validation (Requirement 1.7) is a separate,
 * config-driven check performed against {@code EngineConfig}; it is not enforced
 * here because the valid coordinate range is universal and city-agnostic.
 *
 * @param lat latitude in degrees, within {@code [-90, 90]}
 * @param lng longitude in degrees, within {@code [-180, 180]}
 */
public record Coordinate(double lat, double lng) {

    private static final double MIN_LAT = -90.0;
    private static final double MAX_LAT = 90.0;
    private static final double MIN_LNG = -180.0;
    private static final double MAX_LNG = 180.0;

    public Coordinate {
        if (Double.isNaN(lat) || Double.isInfinite(lat)) {
            throw new IllegalArgumentException("latitude must be a finite number, was: " + lat);
        }
        if (Double.isNaN(lng) || Double.isInfinite(lng)) {
            throw new IllegalArgumentException("longitude must be a finite number, was: " + lng);
        }
        if (lat < MIN_LAT || lat > MAX_LAT) {
            throw new IllegalArgumentException(
                    "latitude must be within [" + MIN_LAT + ", " + MAX_LAT + "], was: " + lat);
        }
        if (lng < MIN_LNG || lng > MAX_LNG) {
            throw new IllegalArgumentException(
                    "longitude must be within [" + MIN_LNG + ", " + MAX_LNG + "], was: " + lng);
        }
    }
}
