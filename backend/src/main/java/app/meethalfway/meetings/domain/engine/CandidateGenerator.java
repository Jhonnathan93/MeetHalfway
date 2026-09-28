package app.meethalfway.meetings.domain.engine;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EngineConfig;
import java.util.List;

/**
 * Generates the {@code Candidate_Point}s evaluated by {@code Grid_Search}
 * (Requirement 5.1). The engine bases its optimization on real travel times to
 * these candidates rather than on a geographic midpoint; this port only decides
 * <em>where</em> to look, not which candidate wins.
 *
 * <p>Two guarantees define the contract and are asserted by Property 11
 * (grid generation count and bounds):
 * <ul>
 *   <li><b>Exactly N points.</b> {@link #generate} returns exactly
 *       {@code config.gridDensityN()} candidates.</li>
 *   <li><b>All in-region.</b> Every returned point lies within the configured
 *       search region derived from the participant origins and constrained by
 *       {@code maxSearchRadiusMeters} and {@code serviceBounds}. The exact region
 *       is exposed via {@link #searchRegion(List, EngineConfig)} so callers and
 *       tests can reason about containment against the same box the generator
 *       used.</li>
 * </ul>
 *
 * <p><b>Determinism.</b> The same {@code origins} and {@code config} always yield
 * identical candidates in identical order (a precondition for the engine's
 * output determinism, Property 10). Implementations must not use randomness,
 * wall-clock time, or iteration order that depends on object identity.
 *
 * <p><b>City-agnosticism.</b> No city-specific value is embedded here: the region
 * origin, extent, and clipping bounds all come from {@link EngineConfig}
 * (Requirements 5.4, 11.5).
 */
public interface CandidateGenerator {

    /**
     * Generates exactly {@code config.gridDensityN()} candidate points, all lying
     * within {@link #searchRegion(List, EngineConfig)} for the same arguments.
     *
     * @param origins the participant origin coordinates; must be non-null and
     *                 non-empty, and every origin must lie within
     *                 {@code config.serviceBounds()}
     * @param config  the externalized engine configuration; must be non-null
     * @return an immutable list of exactly {@code config.gridDensityN()} candidate
     *         coordinates in a deterministic order
     * @throws IllegalArgumentException if {@code origins} is null/empty, contains a
     *                                  null element, or {@code config} is null
     */
    List<Coordinate> generate(List<Coordinate> origins, EngineConfig config);

    /**
     * Derives the bounded search region used by {@link #generate} for the same
     * arguments. Exposed so containment can be reasoned about (Property 11)
     * against exactly the region the generator fills.
     *
     * @param origins the participant origin coordinates; same constraints as
     *                {@link #generate}
     * @param config  the externalized engine configuration; must be non-null
     * @return the search region as a lat/lng box, guaranteed non-empty
     * @throws IllegalArgumentException if arguments are invalid (see
     *                                  {@link #generate})
     */
    SearchRegion searchRegion(List<Coordinate> origins, EngineConfig config);
}
