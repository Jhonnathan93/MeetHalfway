# ADR-002: Optimization Strategies

- Status: Proposed (placeholder)
- Date: TBD
- Deciders: MeetHalfway team

## Context

The engine returns three recommended meeting points — **Fastest**, **Minimax**,
and **Fairest** — each selected by minimizing a metric over real travel times
rather than by picking the geographic midpoint. Selection must be
**deterministic**: the same inputs always produce the same outputs
(Requirements 2–4).

## Decision

TBD — record the mathematical definitions and deterministic tie-break chains
here, including:

- **Fastest** — minimizes `Sum_Time` (Σ tᵢ). Tie-break order:
  `Σ → max → σ → distance-to-centroid`.
- **Minimax** — minimizes `Max_Time` (max tᵢ). Tie-break order:
  `max → Σ → σ → distance-to-centroid`.
- **Fairest** — restricted to the feasible set `Σ ≤ 1.15 · T*`
  (T* = min Σ), then minimizes `Std_Dev` (σ). Tie-break order:
  `σ → Σ → max → distance-to-centroid`.
- **Efficiency_Tolerance** — a fixed 15% constant (`EFFICIENCY_TOLERANCE = 0.15`),
  NOT a configuration field (Requirement 4.3).
- **Equality_Threshold (ε)** — float-comparison tolerance for metric ties,
  `areEqual(a, b) := |a − b| < ε`, with ε ≈ 0.5 minute, read from config.
- **Geographic_Centroid** — arithmetic mean of participant locations, used only
  as the final tie-breaker (applied without ε to guarantee a total order).

## Consequences

- These definitions and tie-break chains are encoded by Properties 5–10.
- The fixed tolerance and config-driven ε keep the strategies deterministic and
  city-agnostic.

## Notes

This is a placeholder ADR created during scaffolding (task 1.1). It will be
completed as the strategy selectors and tie-breaker are implemented
(tasks 5.1–5.4).
