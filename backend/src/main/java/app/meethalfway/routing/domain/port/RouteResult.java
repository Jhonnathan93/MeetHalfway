package app.meethalfway.routing.domain.port;

import app.meethalfway.shared.domain.Minutes;

/**
 * The outcome of a single routing query, modelled as a sealed algebraic type so
 * callers must handle both the success and the failure case explicitly
 * (Requirement 6.1). A {@link RoutingProvider} returns this instead of throwing
 * for expected failures such as timeouts, rate limits, or an unreachable
 * destination.
 *
 * <p>There are exactly two variants:
 * <ul>
 *   <li>{@link Success} — a whole-minute {@link Minutes} travel time (Requirement
 *       2.1, 8.2).</li>
 *   <li>{@link Failure} — a specific, non-blank reason the route could not be
 *       computed, which the engine relays so the affected location is corrected
 *       or removed rather than silently dropped (Requirement 6.1, 6.3, 6.4).</li>
 * </ul>
 */
public sealed interface RouteResult
        permits RouteResult.Success, RouteResult.Failure {

    /**
     * A successful route carrying a whole-minute travel time.
     *
     * @param travelTime the non-null, whole-minute travel time
     */
    record Success(Minutes travelTime) implements RouteResult {

        public Success {
            if (travelTime == null) {
                throw new IllegalArgumentException("travelTime must not be null");
            }
        }
    }

    /**
     * A failed route carrying the specific reason it could not be computed.
     *
     * @param reason the non-blank, human-meaningful reason (e.g. timeout,
     *               unreachable, out of coverage)
     */
    record Failure(String reason) implements RouteResult {

        public Failure {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must be a non-blank explanation");
            }
        }
    }

    /**
     * Convenience factory for a successful route.
     *
     * @param travelTime the whole-minute travel time
     * @return a {@link Success} carrying the given travel time
     */
    static RouteResult success(Minutes travelTime) {
        return new Success(travelTime);
    }

    /**
     * Convenience factory for a failed route.
     *
     * @param reason the non-blank reason the route could not be computed
     * @return a {@link Failure} carrying the given reason
     */
    static RouteResult failure(String reason) {
        return new Failure(reason);
    }
}
