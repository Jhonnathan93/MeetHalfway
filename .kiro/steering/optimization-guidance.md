---
inclusion: auto
name: optimization-guidance
description: Travel-time meeting-point optimization, routing matrices, Fastest/Minimax/Fairest results, fairness trade-offs, candidate generation, tie-breaking, unreachable routes, or participant outlier handling.
---

# Meeting-point optimization rules

Optimize using real routing travel times for every participant and candidate point. Geographic distance and centroid are not optimization objectives; the centroid is used only as a final deterministic tie-breaker.

For candidate point `p`, let `t_i(p)` be participant `i`'s travel time.

- **Fastest:** minimize `sum(t_i(p))`.
- **Minimax:** minimize `max(t_i(p))`.
- **Fairest:** first calculate `T*`, the Fastest optimum. Among candidates whose total time is no more than `FAIREST_EFFICIENCY_TOLERANCE * T*`, choose the one with the smallest standard deviation of travel times. `1.15` is an illustrative default, not a final product value.

For the MVP, use deterministic grid search or a deterministic set of road/intersection candidates. Evaluate total time, maximum time, and standard deviation for every candidate. This is preferred over a continuous solver because it is easier to test and debug.

## Equality and tie-breaking

Use one centralized time-comparison helper. Treat floating-point values as equal when their difference is below `EPSILON_MINUTES` (working value: about 0.5 minute). Do not scatter raw floating-point comparisons across strategies.

Apply this deterministic ordering when primary measures tie:

| Strategy | Primary | Then | Then | Final tie-breaker |
| --- | --- | --- | --- | --- |
| Fastest | lowest total time | lowest maximum time | lowest standard deviation | closest to geographic centroid |
| Minimax | lowest maximum time | lowest total time | lowest standard deviation | closest to geographic centroid |
| Fairest | lowest standard deviation among feasible candidates | lowest total time | lowest maximum time | closest to geographic centroid |

## Routing and outliers

- A candidate is valid only when a route can be calculated for every included participant. Surface a detailed corrective error for each unroutable location; never exclude it silently.
- Detect a potential outlier with a configurable/calibrated rule such as travel time greater than twice the group median. This rule is not finalized.
- When an outlier is found, compute all three strategies both including and excluding that participant. Return enough data to show the changed point and group average-time trade-off.
- Detection is advisory. Never automatically exclude the participant; the user makes the inclusion decision.

Keep candidate search radius and all unfinalized thresholds in configuration. Add property-based tests for invariants such as deterministic results, strategy ordering, valid feasible sets, and correct tie-breaking.
