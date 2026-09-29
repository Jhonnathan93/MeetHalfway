# Recommendation algorithm

The backend validates participant locations against configured service bounds,
then builds exactly `grid-density-n` candidate points in the participant bounding
box expanded by the configured search radius and clipped to those bounds. Points
are selected in centre/mirror pairs so an arbitrary candidate count cannot bias
the search toward one corner. The default is a 5x5 grid (25 candidates). Grid
generation is deterministic and city-agnostic.

The routing provider requests a duration matrix for every participant/candidate
pair. OSRM uses its Table API and splits unusually large grids into bounded
requests; simpler providers can use the port's pair-by-pair fallback. Durations
are rounded to whole minutes to preserve the current API and persistence
contract. Any failed participant route makes the entire recommendation a routing
failure; no partial result is returned or stored.

Each fully-routed candidate receives three metrics:

- `sumTime`: sum of participant travel times;
- `maxTime`: longest participant travel time;
- `stdDev`: population standard deviation of travel times.

The selectors optimize different priorities: Fastest minimizes total time,
Minimax minimizes the longest trip, and Fairest minimizes spread among candidates
within the domain's efficiency tolerance. Each uses the configured epsilon for
metric ties: candidates within epsilon of the current priority's minimum remain
eligible for the next metric. The geographic centroid and then exact coordinates
provide the final deterministic tie-breaks.

Outliers are detected from the Fastest result's participant times. A participant
is flagged only when their time strictly exceeds `k × median`. When any are
flagged, the remaining participants get a new search region, candidate grid,
routing matrix, centroid and all three strategy selections. This prevents the
excluded participant's location from continuing to shape the reduced group's
recommended point. The primary results continue to include everyone; the
response presents both variants and their average travel-time trade-off.

See [ADR-002](decisions/ADR-002-optimization-strategies.md) for strategy rationale
and the jqwik property tests under `backend/src/test` for executable invariants.
