package app.meethalfway.domain.testing;

import app.meethalfway.domain.model.Coordinate;
import app.meethalfway.domain.model.Minutes;
import app.meethalfway.domain.model.TransportMode;
import app.meethalfway.domain.port.RouteResult;
import app.meethalfway.domain.port.RoutingProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic, in-memory {@link RoutingProvider} for engine tests.
 *
 * <p>This fake makes the recommendation-engine property tests offline and
 * reproducible: given the same inputs and configuration it always returns the
 * same travel-time matrix, which is the precondition for the Determinism
 * property (design Property 10) and the optimality properties. No network,
 * clock, or randomness is involved.
 *
 * <p>The port only receives {@code (origin, destination, mode)} — there is no
 * participant id — so this fake keys all behavior on the <em>origin</em>
 * {@link Coordinate}, which is exactly a participant's starting location. Tests
 * therefore address a participant by their origin coordinate.
 *
 * <h2>Travel-time model</h2>
 * By default the whole-minute travel time is a deterministic function of the
 * straight-line distance between origin and destination, scaled per transport
 * mode (walking is slower than driving). Because it is a pure function of the
 * two coordinates and the mode, repeated queries are identical.
 *
 * <h2>Engineering test scenarios (builder API)</h2>
 * <ul>
 *   <li><b>ε-ties</b> — {@link Builder#withFixedTime} pins a whole-minute time
 *       for a specific {@code (origin, destination)} pair. Pinning two
 *       candidates to times whose per-participant sums/maxima/std-devs differ by
 *       less than {@code EngineConfig.epsilonMinutes} engineers a metric tie the
 *       tie-breaker must resolve.</li>
 *   <li><b>Injected outliers</b> — {@link Builder#withOutlierOrigin} multiplies
 *       every travel time <em>from</em> a given origin by a large factor, making
 *       that participant an exaggerated outlier for every candidate while
 *       remaining fully deterministic.</li>
 *   <li><b>Injected failures</b> — {@link Builder#withFailingOrigin} makes every
 *       query <em>from</em> a given origin return {@link RouteResult.Failure},
 *       enabling routing-failure transparency tests (a participant location that
 *       cannot be routed must be surfaced, never silently dropped).</li>
 * </ul>
 *
 * <p>Instances are immutable after {@link Builder#build()} and safe to reuse
 * across property iterations.
 */
public final class FakeRoutingProvider implements RoutingProvider {

    /**
     * Base minutes accrued per unit of coordinate distance for driving. Chosen
     * so typical intra-city coordinate deltas map to human-plausible whole
     * minutes; the exact value is irrelevant to correctness, only its
     * determinism matters.
     */
    private static final double DRIVING_MINUTES_PER_DEGREE = 600.0;

    /** Walking is modelled as this multiple slower than driving. */
    private static final double WALKING_SLOWDOWN = 4.0;

    private final Map<PairKey, Integer> fixedTimes;
    private final Map<Coordinate, Double> outlierOrigins;
    private final Map<Coordinate, String> failingOrigins;
    private final double drivingMinutesPerDegree;
    private final double walkingSlowdown;

    private FakeRoutingProvider(Builder builder) {
        this.fixedTimes = Map.copyOf(builder.fixedTimes);
        this.outlierOrigins = Map.copyOf(builder.outlierOrigins);
        this.failingOrigins = Map.copyOf(builder.failingOrigins);
        this.drivingMinutesPerDegree = builder.drivingMinutesPerDegree;
        this.walkingSlowdown = builder.walkingSlowdown;
    }

    /**
     * Creates a fake with the default distance-based travel-time model and no
     * engineered scenarios.
     *
     * @return a ready-to-use provider
     */
    public static FakeRoutingProvider withDefaults() {
        return builder().build();
    }

    /**
     * @return a new {@link Builder} for configuring engineered scenarios
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public RouteResult travelTime(Coordinate origin, Coordinate destination, TransportMode mode) {
        if (origin == null || destination == null || mode == null) {
            throw new IllegalArgumentException("origin, destination and mode must not be null");
        }

        String failureReason = failingOrigins.get(origin);
        if (failureReason != null) {
            return RouteResult.failure(failureReason);
        }

        Integer pinned = fixedTimes.get(new PairKey(origin, destination));
        if (pinned != null) {
            return RouteResult.success(new Minutes(pinned));
        }

        double base = baseMinutes(origin, destination, mode);
        double multiplier = outlierOrigins.getOrDefault(origin, 1.0);
        int minutes = (int) Math.round(base * multiplier);
        return RouteResult.success(new Minutes(Math.max(0, minutes)));
    }

    private double baseMinutes(Coordinate origin, Coordinate destination, TransportMode mode) {
        double dLat = origin.lat() - destination.lat();
        double dLng = origin.lng() - destination.lng();
        double distance = Math.hypot(dLat, dLng);
        double perDegree = drivingMinutesPerDegree
                * (mode == TransportMode.WALKING ? walkingSlowdown : 1.0);
        return distance * perDegree;
    }

    /** Builder for {@link FakeRoutingProvider}. */
    public static final class Builder {
        private final Map<PairKey, Integer> fixedTimes = new HashMap<>();
        private final Map<Coordinate, Double> outlierOrigins = new HashMap<>();
        private final Map<Coordinate, String> failingOrigins = new HashMap<>();
        private double drivingMinutesPerDegree = DRIVING_MINUTES_PER_DEGREE;
        private double walkingSlowdown = WALKING_SLOWDOWN;

        private Builder() {
        }

        /**
         * Pins an exact whole-minute travel time for one {@code (origin,
         * destination)} pair, overriding the distance model. Use this to
         * engineer precise metric values (and thus ε-ties) for specific
         * candidates.
         *
         * @param origin      the participant origin
         * @param destination the candidate point
         * @param minutes     the non-negative whole-minute time to return
         * @return this builder
         */
        public Builder withFixedTime(Coordinate origin, Coordinate destination, int minutes) {
            if (minutes < 0) {
                throw new IllegalArgumentException("minutes must be non-negative, was: " + minutes);
            }
            fixedTimes.put(new PairKey(requireCoord(origin, "origin"),
                    requireCoord(destination, "destination")), minutes);
            return this;
        }

        /**
         * Marks an origin as an injected outlier: every travel time from it is
         * multiplied by {@code factor}, making that participant exaggeratedly
         * far for all candidates while staying deterministic. Ignored for pairs
         * pinned via {@link #withFixedTime}.
         *
         * @param origin the participant origin to exaggerate
         * @param factor the strictly-positive multiplier (e.g. {@code 5.0})
         * @return this builder
         */
        public Builder withOutlierOrigin(Coordinate origin, double factor) {
            if (!Double.isFinite(factor) || factor <= 0.0) {
                throw new IllegalArgumentException(
                        "factor must be finite and strictly positive, was: " + factor);
            }
            outlierOrigins.put(requireCoord(origin, "origin"), factor);
            return this;
        }

        /**
         * Marks an origin as unroutable: every query from it returns a
         * {@link RouteResult.Failure} carrying {@code reason}. Enables
         * routing-failure transparency tests.
         *
         * @param origin the participant origin that cannot be routed
         * @param reason the non-blank failure reason to surface
         * @return this builder
         */
        public Builder withFailingOrigin(Coordinate origin, String reason) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must be a non-blank explanation");
            }
            failingOrigins.put(requireCoord(origin, "origin"), reason);
            return this;
        }

        /**
         * Overrides the driving minutes-per-degree scale of the distance model.
         * Rarely needed; the default already yields plausible whole minutes.
         *
         * @param minutesPerDegree strictly-positive, finite scale
         * @return this builder
         */
        public Builder withDrivingMinutesPerDegree(double minutesPerDegree) {
            if (!Double.isFinite(minutesPerDegree) || minutesPerDegree <= 0.0) {
                throw new IllegalArgumentException(
                        "minutesPerDegree must be finite and strictly positive, was: "
                                + minutesPerDegree);
            }
            this.drivingMinutesPerDegree = minutesPerDegree;
            return this;
        }

        /**
         * Overrides how much slower walking is than driving in the distance
         * model.
         *
         * @param slowdown strictly-positive, finite multiplier ({@code >= 1}
         *                 keeps walking slower than driving)
         * @return this builder
         */
        public Builder withWalkingSlowdown(double slowdown) {
            if (!Double.isFinite(slowdown) || slowdown <= 0.0) {
                throw new IllegalArgumentException(
                        "slowdown must be finite and strictly positive, was: " + slowdown);
            }
            this.walkingSlowdown = slowdown;
            return this;
        }

        /**
         * @return an immutable {@link FakeRoutingProvider} with the configured
         *         scenarios
         */
        public FakeRoutingProvider build() {
            return new FakeRoutingProvider(this);
        }

        private static Coordinate requireCoord(Coordinate coordinate, String name) {
            if (coordinate == null) {
                throw new IllegalArgumentException(name + " must not be null");
            }
            return coordinate;
        }
    }

    /** Composite key pairing an origin with a destination for pinned times. */
    private record PairKey(Coordinate origin, Coordinate destination) {
        private PairKey {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(destination, "destination");
        }
    }
}
