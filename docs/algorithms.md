# Recommendation algorithm

The backend validates participant locations against configured service bounds,
then builds exactly `grid-density-n` candidate points in the participant bounding
box expanded by the configured search radius and clipped to those bounds. Grid
generation is deterministic and city-agnostic.

For every participant/candidate pair, the routing provider returns a whole-minute
travel time or an explicit failure. Any failed participant route makes the entire
recommendation a routing failure; no partial result is returned or stored.

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
flagged, all three strategies are recalculated without them while the primary
results continue to include everyone; the response presents both variants and
their average travel-time trade-off.

See [ADR-002](decisions/ADR-002-optimization-strategies.md) for strategy rationale
and the jqwik property tests under `backend/src/test` for executable invariants.
