# ADR-001: Routing Provider

- Status: Proposed (placeholder)
- Date: TBD
- Deciders: MeetHalfway team

## Context

The recommendation engine computes real `Travel_Time` between participant
origins and candidate meeting points using the meeting's `Transport_Mode`
(driving or walking). All contact with an external routing service happens
through the `RoutingProvider` port (see `design.md`), so the concrete provider
is an edge concern that can be revisited without touching the domain core.

The MVP constraint is that only free / open-source routing technologies are
used. Free services with rate limits are acceptable. Any API key is held
backend-only and never exposed to the frontend (Requirement 11.4).

## Decision

TBD — record the chosen free/open-source routing service behind the
`RoutingProvider` port here, including:

- The selected service and why it was chosen.
- Its rate limits and free-tier constraints.
- How timeouts and rate-limit responses map to typed routing failures
  (Requirement 6.1).
- The fallback plan if the primary provider is unavailable.

## Consequences

- The `RoutingProvider` port isolates this choice; swapping providers is an
  adapter change, not a core change (Requirements 5.4, 11.5).
- Failure semantics of the chosen provider drive Property 13
  (routing-failure transparency).

## Notes

This is a placeholder ADR created during scaffolding (task 1.1). It will be
completed when the routing adapter is implemented (task 10.1).
