package app.meethalfway.meetings.domain.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.ServiceBounds;

/**
 * Deterministic {@code Grid_Search} candidate generator
 * (Requirements 5.1, 5.4).
 *
 * <p><b>Region derivation</b> ({@link #searchRegion}). The region is the
 * axis-aligned bounding box of the participant origins, expanded outward by the
 * configured {@code maxSearchRadiusMeters} (converted to degrees), then clipped
 * to the configured {@code serviceBounds}. Because every origin is required to be
 * inside {@code serviceBounds}, the origins' bounding box is a subset of the
 * service bounds, so the clipped region is always non-empty (it contains at
 * least the origins). The metre-to-degree conversion uses a spherical
 * approximation: one latitude degree ≈ {@value #METERS_PER_DEGREE_LAT} m, and one
 * longitude degree ≈ that value scaled by {@code cos(latitude)} at the region's
 * mid-latitude. This is intentionally city-agnostic — every input comes from
 * {@link EngineConfig} and the origins, nothing is hard-coded to a city.
 *
 * <p><b>Point placement</b> ({@link #generate}). Exactly {@code gridDensityN}
 * points are produced on a square lattice spread evenly across the search
 * region. Points are selected in mirrored pairs around the region centre, with
 * the centre included for odd values of N, so a partial grid cannot bias the
 * search toward one corner. When {@code N == 1} the single point is the region
 * centre. When an axis is degenerate (min == max) samples collapse to the shared
 * bound. All produced points therefore lie within the region returned by
 * {@link #searchRegion}, satisfying Property 11.
 *
 * <p><b>Determinism.</b> No randomness, clock, or identity-dependent ordering is
 * used; identical {@code origins} and {@code config} always yield the identical
 * list in the identical order (a precondition for Property 10).
 */
public final class GridCandidateGenerator {

    /** Metres per degree of latitude (spherical approximation). */
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    public List<Coordinate> generate(List<Coordinate> origins, EngineConfig config) {
        SearchRegion region = searchRegion(origins, config);
        int n = config.gridDensityN();

        List<Coordinate> candidates = new ArrayList<>(n);

        if (n == 1) {
            candidates.add(midpoint(region));
            return List.copyOf(candidates);
        }

        int side = (int) Math.ceil(Math.sqrt((double) n));
        if ((n & 1) == 1 && (side & 1) == 0) {
            side++;
        }

        int centerIndex = side / 2;
        Coordinate center = midpoint(region);
        if ((n & 1) == 1) {
            candidates.add(center);
        }

        List<GridPair> pairs = new ArrayList<>(Math.min(n / 2, 1024));
        for (int row = 0; row < side; row++) {
            for (int col = 0; col < side; col++) {
                int oppositeRow = side - 1 - row;
                int oppositeCol = side - 1 - col;
                long index = (long) row * side + col;
                long oppositeIndex = (long) oppositeRow * side + oppositeCol;
                if (index >= oppositeIndex || (row == centerIndex && col == centerIndex)) {
                    continue;
                }

                int rowDistance = row - centerIndex;
                int colDistance = col - centerIndex;
                double radiusSquared = (double) rowDistance * rowDistance
                        + (double) colDistance * colDistance;
                Coordinate point = sample(region, row, col, side);
                Coordinate opposite = new Coordinate(
                        clamp(2.0 * center.lat() - point.lat(), region.minLat(), region.maxLat()),
                        clamp(2.0 * center.lng() - point.lng(), region.minLng(), region.maxLng()));
                pairs.add(new GridPair(point, opposite, radiusSquared, row, col));
            }
        }

        pairs.sort(Comparator.comparingDouble(GridPair::radiusSquared)
                .thenComparingInt(GridPair::row)
                .thenComparingInt(GridPair::col));
        int requiredPairs = n / 2;
        for (int i = 0; i < requiredPairs; i++) {
            GridPair pair = pairs.get(i);
            candidates.add(pair.first());
            candidates.add(pair.opposite());
        }

        return List.copyOf(candidates);
    }

    private static Coordinate sample(SearchRegion region, int row, int col, int side) {
        double lat = axisSample(region.minLat(), region.maxLat(), row, side);
        double lng = axisSample(region.minLng(), region.maxLng(), col, side);
        // Clamp samples to the exact region box in case floating-point arithmetic
        // drifts one ULP beyond an inclusive edge.
        return new Coordinate(
                clamp(lat, region.minLat(), region.maxLat()),
                clamp(lng, region.minLng(), region.maxLng()));
    }

    private record GridPair(
            Coordinate first, Coordinate opposite, double radiusSquared, int row, int col) {}

    public SearchRegion searchRegion(List<Coordinate> origins, EngineConfig config) {
        validate(origins, config);

        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double minLng = Double.POSITIVE_INFINITY;
        double maxLng = Double.NEGATIVE_INFINITY;
        for (Coordinate origin : origins) {
            minLat = Math.min(minLat, origin.lat());
            maxLat = Math.max(maxLat, origin.lat());
            minLng = Math.min(minLng, origin.lng());
            maxLng = Math.max(maxLng, origin.lng());
        }

        double midLat = (minLat + maxLat) / 2.0;
        double latDelta = config.maxSearchRadiusMeters() / METERS_PER_DEGREE_LAT;
        double metersPerDegreeLng = METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(midLat));
        double lngDelta =
                metersPerDegreeLng <= 0.0
                        ? 0.0
                        : config.maxSearchRadiusMeters() / metersPerDegreeLng;

        double expandedMinLat = minLat - latDelta;
        double expandedMaxLat = maxLat + latDelta;
        double expandedMinLng = minLng - lngDelta;
        double expandedMaxLng = maxLng + lngDelta;

        // Clip to the configured service bounds. The origins are all inside the
        // service bounds, so this intersection is guaranteed non-empty.
        ServiceBounds bounds = config.serviceBounds();
        double clippedMinLat = Math.max(expandedMinLat, bounds.minLat());
        double clippedMaxLat = Math.min(expandedMaxLat, bounds.maxLat());
        double clippedMinLng = Math.max(expandedMinLng, bounds.minLng());
        double clippedMaxLng = Math.min(expandedMaxLng, bounds.maxLng());

        return new SearchRegion(clippedMinLat, clippedMaxLat, clippedMinLng, clippedMaxLng);
    }

    /**
     * Evenly spaced sample position {@code index} of {@code count} across
     * {@code [min, max]}, inclusive of both edges. With a single sample, or a
     * degenerate axis where {@code min == max}, the shared bound is returned.
     */
    private static double axisSample(double min, double max, int index, int count) {
        if (count <= 1 || min == max) {
            return min;
        }
        double fraction = (double) index / (double) (count - 1);
        return min + (max - min) * fraction;
    }

    private static Coordinate midpoint(SearchRegion region) {
        double lat = (region.minLat() + region.maxLat()) / 2.0;
        double lng = (region.minLng() + region.maxLng()) / 2.0;
        return new Coordinate(clampLat(lat), clampLng(lng));
    }

    /** Constrains {@code value} to {@code [min, max]} (inclusive). */
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Guards against floating-point drift pushing a sample past the universal range. */
    private static double clampLat(double lat) {
        return Math.max(-90.0, Math.min(90.0, lat));
    }

    private static double clampLng(double lng) {
        return Math.max(-180.0, Math.min(180.0, lng));
    }

    private static void validate(List<Coordinate> origins, EngineConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (origins == null || origins.isEmpty()) {
            throw new IllegalArgumentException("origins must not be null or empty");
        }
        for (Coordinate origin : origins) {
            if (origin == null) {
                throw new IllegalArgumentException("origins must not contain a null coordinate");
            }
        }
    }
}
