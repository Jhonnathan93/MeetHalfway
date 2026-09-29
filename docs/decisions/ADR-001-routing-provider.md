# ADR-001: Routing provider

- Status: Accepted
- Date: 2026-09-28
- Deciders: MeetHalfway team

## Context

Recommendations require travel-time estimates for every participant and
candidate. Routing is an external dependency and must not leak into the domain
engine or frontend. A partial set of routes is not a valid recommendation.

## Decision

Use OSRM through the `RoutingProvider` port. The default configuration targets
the public OSRM demo service; the base URL and driving/walking profiles are
configurable so a deployment can point at an appropriately provisioned router.
The adapter reads route durations, rounds them to whole minutes, and maps
timeouts, HTTP 429, non-2xx responses, malformed responses, and no-route results
to explicit routing failures. Interruptions propagate after restoring the thread
interrupt flag.

The adapter does not retry or silently switch providers. A failed participant
route fails the complete recommendation, and that failure is not persisted. A
provider API key is optional and, when used, is read and sent only by the
backend.

The public demo service is for development and has no application SLA or
guaranteed profile availability. Production deployments must configure a router
whose capacity and profiles meet their requirements, such as a self-hosted OSRM
instance. Replacing it remains an adapter change behind `RoutingProvider`.

## Consequences

- The optimizer depends on the small `RoutingProvider` boundary, not HTTP or
  OSRM response details.
- Expected provider failures remain visible to callers as the existing HTTP 422
  contract; no partial recommendation is returned or stored.
- Provider configuration and credentials remain backend-only.
