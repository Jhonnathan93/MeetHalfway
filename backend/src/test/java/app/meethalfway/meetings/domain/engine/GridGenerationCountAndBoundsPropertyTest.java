package app.meethalfway.meetings.domain.engine;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.OutlierRule;
import app.meethalfway.meetings.domain.model.ServiceBounds;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for the grid generation count-and-bounds invariant of
 * {@link GridCandidateGenerator}.
 *
 * <p>Feature: meeting-recommendation-engine, Property 11: Grid generation count
 * and bounds &mdash; for any engine configuration, {@code Grid_Search} generates
 * exactly {@code N} candidate points and every generated point lies within the
 * configured search region.
 *
 * <p>Validates: Requirements 5.1
 *
 * <p>Each scenario is generated as a coherent whole: an arbitrary
 * {@link ServiceBounds}, a set of 2&ndash;10 participant origins constrained to
 * lie within those bounds, a {@code gridDensityN >= 1}, a strictly-positive
 * {@code maxSearchRadiusMeters}, and a valid {@link OutlierRule}/epsilon. Rather
 * than re-deriving the region, the test asserts containment against the exact
 * box the generator exposes via {@link GridCandidateGenerator#searchRegion}.
 */
class GridGenerationCountAndBoundsPropertyTest {

    private final GridCandidateGenerator generator = new GridCandidateGenerator();

    /**
     * Property 11 &mdash; for every valid configuration, {@code generate}
     * produces exactly {@code gridDensityN} points and each point is contained
     * in the search region returned for the same origins and config.
     */
    @Property(tries = 100)
    void generatesExactlyNPointsAllWithinTheSearchRegion(@ForAll("scenarios") Scenario scenario) {
        List<Coordinate> origins = scenario.origins();
        EngineConfig config = scenario.config();

        List<Coordinate> candidates = generator.generate(origins, config);
        SearchRegion region = generator.searchRegion(origins, config);

        assertThat(candidates).hasSize(config.gridDensityN());
        assertThat(candidates).allSatisfy(point -> assertThat(region.contains(point)).isTrue());
    }

    @Provide
    Arbitrary<Scenario> scenarios() {
        return serviceBounds()
                .flatMap(
                        bounds ->
                                Combinators.combine(
                                                originsWithin(bounds),
                                                Arbitraries.integers().between(1, 200),
                                                Arbitraries.doubles().between(1.0, 50_000.0),
                                                outlierRules(),
                                                Arbitraries.doubles().between(0.1, 5.0))
                                        .as(
                                                (origins, n, radius, rule, epsilon) ->
                                                        new Scenario(
                                                                origins,
                                                                new EngineConfig(
                                                                        bounds, n, radius, rule, epsilon))));
    }

    /**
     * Builds an arbitrary service-bounds box with a non-degenerate span on each
     * axis, kept comfortably inside the universal coordinate ranges so origins
     * generated within it are always constructible.
     */
    private static Arbitrary<ServiceBounds> serviceBounds() {
        Arbitrary<Double> minLat = Arbitraries.doubles().between(-89.0, 88.0).ofScale(6);
        Arbitrary<Double> minLng = Arbitraries.doubles().between(-179.0, 178.0).ofScale(6);
        Arbitrary<Double> latSpan = Arbitraries.doubles().between(0.0001, 1.0).ofScale(6);
        Arbitrary<Double> lngSpan = Arbitraries.doubles().between(0.0001, 1.0).ofScale(6);
        return Combinators.combine(minLat, minLng, latSpan, lngSpan)
                .as(
                        (loLat, loLng, dLat, dLng) ->
                                new ServiceBounds(
                                        loLat,
                                        Math.min(90.0, loLat + dLat),
                                        loLng,
                                        Math.min(180.0, loLng + dLng)));
    }

    /**
     * Generates 2&ndash;10 origins whose coordinates all fall within the given
     * service bounds, so the generator's precondition (origins inside bounds) is
     * satisfied.
     */
    private static Arbitrary<List<Coordinate>> originsWithin(ServiceBounds bounds) {
        // Generate each origin as a fraction in [0, 1] of the bounds span on each
        // axis and compute the coordinate arithmetically. This keeps the raw
        // generated values on a fixed [0, 1] scale, avoiding jqwik's decimal-scale
        // constraint that trips when a generated range has awkward boundaries,
        // while still guaranteeing every origin lies within the service bounds.
        Arbitrary<Double> fraction = Arbitraries.doubles().between(0.0, 1.0).ofScale(6);
        Arbitrary<Coordinate> coordinate =
                Combinators.combine(fraction, fraction)
                        .as(
                                (latFraction, lngFraction) ->
                                        new Coordinate(
                                                lerp(bounds.minLat(), bounds.maxLat(), latFraction),
                                                lerp(bounds.minLng(), bounds.maxLng(), lngFraction)));
        return coordinate.list().ofMinSize(2).ofMaxSize(10);
    }

    /** Linear interpolation clamped so the result never drifts outside [min, max]. */
    private static double lerp(double min, double max, double fraction) {
        double value = min + (max - min) * fraction;
        return Math.max(min, Math.min(max, value));
    }

    private static Arbitrary<OutlierRule> outlierRules() {
        Arbitrary<OutlierRule> medianMultiple =
                Arbitraries.doubles().between(0.1, 10.0).map(OutlierRule::new);
        return medianMultiple;
    }

    /** A coherent, always-valid generation scenario. */
    record Scenario(List<Coordinate> origins, EngineConfig config) {
        Scenario {
            origins = new ArrayList<>(origins);
        }
    }
}
