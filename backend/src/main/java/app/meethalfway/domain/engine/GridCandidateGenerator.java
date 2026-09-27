package app.meethalfway.domain.engine;

import java.util.ArrayList;
import java.util.List;

import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.EngineConfig;
import app.meethalfway.domain.model.ServiceBounds;

/**
 * Deterministic {@code Grid_Search} implementation of {@link CandidateGenerator}
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
 * points are produced. Let {@code cols = ceil(sqrt(N))} and
 * {@code rows = ceil(N / cols)}; the region is sampled on a {@code rows × cols}
 * lattice with points spread evenly across each axis inclusive of both edges,
 * emitted in row-major order (increasing latitude, then increasing longitude),
 * and the first {@code N} are kept. When {@code N == 1} the single point is the
 * region centre. When an axis is degenerate (min == max) every sample on that
 * axis collapses to the shared bound. All produced points therefore lie within
 * the region returned by {@link #searchRegion}, satisfying Property 11.
 *
 * <p><b>Determinism.</b> No randomness, clock, or identity-dependent ordering is
 * used; identical {@code origins} and {@code config} always yield the identical
 * list in the identical order (a precondition for Property 10).
 */
public final class GridCandidateGenerator implements CandidateGenerator {

    /** Metres per degree of latitude (spherical approximation). */
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    @Override
    public List<Coordinate> generate(List<Coordinate> origins, EngineConfig config) {
        SearchRegion region = searchRegion(origins, config);
        int n = config.gridDensityN();

        List<Coordinate> candidates = new ArrayList<>(n);

        if (n == 1) {
            candidates.add(midpoint(region));
            return List.copyOf(candidates);
        }

        int cols = (int) Math.ceil(Math.sqrt((double) n));
        int rows = (int) Math.ceil((double) n / (double) cols);

        for (int r = 0; r < rows && candidates.size() < n; r++) {
            double lat = axisSample(region.minLat(), region.maxLat(), r, rows);
            for (int c = 0; c < cols && candidates.size() < n; c++) {
                double lng = axisSample(region.minLng(), region.maxLng(), c, cols);
                // Clamp each sample to the region's own bounds. axisSample computes
                // min + (max - min) * fraction, which at the far edge (fraction == 1)
                // can drift ~1 ULP above max, escaping region.contains. Clamping to
                // the exact region box guarantees every emitted point is contained.
                candidates.add(
                        new Coordinate(
                                clamp(lat, region.minLat(), region.maxLat()),
                                clamp(lng, region.minLng(), region.maxLng())));
            }
        }

        return List.copyOf(candidates);
    }

    @Override
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
