# ADR-002: Recommendation strategies

- Status: Accepted
- Date: 2026-09-28
- Deciders: MeetHalfway team

## Context

The application returns one meeting point for each of three different
priorities. The scores are based on routed whole-minute travel times, not
geographic distance alone. Results must be deterministic for the same inputs.

## Decision

Evaluate all candidates using the same participant travel-time vector and
select each strategy independently:

- **Fastest:** minimize total travel time (`sumTime`), then compare `maxTime`,
  standard deviation, distance to the geographic centroid, and exact latitude
  and longitude.
- **Minimax:** minimize the longest participant trip (`maxTime`), then compare
  `sumTime`, standard deviation, centroid distance, and exact coordinates.
- **Fairest:** first retain candidates whose `sumTime` is at most
  `1.15 × T* + ε`, where `T*` is the minimum `sumTime`; then minimize standard
  deviation, `sumTime`, `maxTime`, centroid distance, and exact coordinates.

Metric comparisons use configured `epsilonMinutes` (default 0.5); two values
whose difference is strictly less than epsilon compare equal. At each priority,
selection keeps candidates within epsilon of that stage's minimum before
advancing to the next metric. This avoids using a pairwise epsilon comparator,
whose ties are not transitive and could make the selected point depend on grid
iteration order. The final centroid-distance and exact-coordinate comparisons
are not epsilon-based, which keeps selection deterministic, including
candidates equidistant from the centroid. The 15% Fairest efficiency tolerance
is a fixed domain constant, not a deployment setting.

Outliers are detected from the Fastest result using a configurable multiple of
the median participant travel time (default `k = 2`). A participant is flagged
only when their time is strictly greater than `k × median`. When any are found,
the application presents all-participant recommendations alongside a second
calculation that generates a fresh candidate grid from only the remaining
participants, then re-routes and selects all three strategies on that grid. The
outlier never silently disappears from the primary result.

## Consequences

- Each selector is a small concrete domain class for a distinct business rule;
  no generic selector plug-in framework is needed.
- Jqwik properties protect optimality, feasibility, deterministic tie-breaking,
  and outlier transparency.
- Balanced candidate grids and batched OSRM Table requests improve search
  coverage without multiplying one-request-per-pair network calls.
- The strategy response fields remain part of the current API contract even
  where their information is derivable by clients.
