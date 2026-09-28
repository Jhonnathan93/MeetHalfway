# Implementation Plan: Meeting Recommendation Engine

## Overview

This plan turns the [design](./design.md) and [requirements](./requirements.md) into incremental coding steps. The build order follows the Clean Architecture dependency rule (domain → application → adapters → web/frontend): we scaffold the monorepo and Docker Compose first, then build the pure domain core (value objects, ports, engine components) with deterministic fakes, then persistence and provider adapters, then the application use cases, then the REST web adapter with cross-cutting concerns, and finally the Vue 3 frontend with i18n and dark mode.

Property-based testing is mandatory for the engine. Each of the 17 correctness properties from the design is implemented as a single property-based test with a minimum of 100 iterations, using deterministic in-memory fakes for `RoutingProvider`/`GeocodingProvider`, and tagged in the format `Feature: meeting-recommendation-engine, Property {number}: {property_text}`. Non-engine areas (UI, i18n, CRUD, rate limiting, CORS, provider integration) use example, integration, and snapshot tests as described in the design's Testing Strategy.

Backend: Spring Boot (Java), jqwik for PBT, strict SOLID + explicit DI. Frontend: Vue 3 + TypeScript (`strict: true`), Vitest + fast-check + Vue Test Utils. Database: PostgreSQL.

Conventions:
- Tasks marked with `*` are optional (tests and post-MVP stubs). Core engine, API, and minimal UI path are required.
- Each task cites the requirement clause(s) and/or design component it implements.
- Property test tasks name the exact property number and its `Validates` requirements.

## Tasks

- [x] 1. Scaffold monorepo, Docker Compose, and backend/frontend skeletons
  - [x] 1.1 Create monorepo structure and container/edge configuration
    - Create `meethalfway/` layout: `frontend/`, `backend/`, `nginx/`, `docs/`, `docs/decisions/`
    - Author `docker-compose.yml` with services for backend, frontend, PostgreSQL, and Nginx reverse proxy routing `/api/v1` to the backend
    - Add Nginx config that terminates HTTP and applies an explicit CORS policy permitting only configured origins
    - Add placeholder ADR files `docs/decisions/ADR-001-routing-provider.md` and `docs/decisions/ADR-002-optimization-strategies.md`
    - _Requirements: 11.1, 11.3, 11.5_

  - [x] 1.2 Initialize Spring Boot backend with Clean Architecture layers
    - Set up the backend build (Maven/Gradle) with `domain`, `application`, `adapters`, `config` modules/packages and enforce the inward dependency rule
    - Add Spring Boot, JPA/PostgreSQL driver, validation, and jqwik (test scope) dependencies with pinned versions
    - Configure explicit Dependency Injection wiring points (no field injection; constructor injection only)
    - _Requirements: 5.4, 11.5_

  - [x] 1.3 Initialize Vue 3 + TypeScript (strict) frontend project
    - Create the Vue 3 SPA with TypeScript `strict: true`, Vite build, and Vitest + fast-check + Vue Test Utils configured
    - Add a dark-mode-default theme scaffold and desktop-first responsive base layout
    - _Requirements: 10.1, 10.2_

- [x] 2. Implement domain value objects and their invariants
  - [x] 2.1 Implement core value objects with construction-time invariants
    - Implement `Coordinate` (lat in [-90,90], lng in [-180,180]), `TransportMode` enum (DRIVING, WALKING), `Minutes` (integer, non-negative), `ParticipantId`, `ParticipantInput`, and `MeetingInput` (2–10 participants)
    - Re-validate invariants in constructors so an invalid object cannot be built
    - Define the domain constant `EFFICIENCY_TOLERANCE = 0.15` (not configurable)
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.6, 4.3, 8.2_

  - [x] 2.2 Implement result and evaluation value objects
    - Implement `EvaluatedCandidate`, `StrategyResult`, `StrategyResults`, `OutlierTradeoff`, and the sealed `RecommendationOutcome` (`Success` / `RoutingFailure`) with `RoutingError`
    - _Requirements: 6.1, 7.2, 7.3, 8.1, 8.2, 8.3_

  - [x] 2.3 Write property test for participant-count validation
    - **Property 1: Participant-count validation invariant** — engine accepts only counts 2–10 inclusive; below/above rejected with a bound-identifying error
    - **Validates: Requirements 1.1, 1.2, 1.3**
    - jqwik, min 100 iterations; tag `Feature: meeting-recommendation-engine, Property 1: ...`

  - [x] 2.4 Write property test for transport-mode validation
    - **Property 2: Transport-mode validation invariant** — accepted only for exactly "driving"/"walking"; otherwise rejected naming supported modes
    - **Validates: Requirements 1.4, 1.5**
    - jqwik, min 100 iterations; tagged per convention

  - [x] 2.5 Write property test for travel-time units and non-negativity
    - **Property 16: Travel-time units and non-negativity** — every computed travel time is a whole, non-negative number of minutes
    - **Validates: Requirements 8.2**
    - jqwik, min 100 iterations; tagged per convention

- [x] 3. Define ports and the EngineConfig
  - [x] 3.1 Define outbound ports (interfaces) in the domain
    - Define `RoutingProvider` (returns typed `RouteResult`: minutes or failure reason), `GeocodingProvider` (`autocomplete`, `resolve`), `PlacesProvider` (post-MVP), and `MeetingRepository`
    - _Requirements: 2.1, 5.2, 6.1, 9.1, 9.2, 9.5, 9.7_

  - [x] 3.2 Implement EngineConfig and OutlierRule as externalized configuration
    - Implement `EngineConfig` (`serviceBounds`, `gridDensityN`, `maxSearchRadiusMeters`, `outlierRule`, `epsilonMinutes`) and `OutlierRule` variants (median-multiple `k`, percentile `p90`)
    - Ensure no Medellín-specific value is embedded; `EFFICIENCY_TOLERANCE` stays a constant, not a config field
    - _Requirements: 4.3, 5.4, 11.5_

  - [x] 3.3 Write deterministic in-memory fakes for RoutingProvider and GeocodingProvider
    - Fakes return reproducible travel-time matrices (support engineered ε-ties and injected outliers) to make engine property tests offline and deterministic
    - _Requirements: 2.1, 5.2, 9.7_

- [x] 4. Implement candidate generation (Grid_Search) and metric computation
  - [x] 4.1 Implement CandidateGenerator (deterministic Grid_Search)
    - Generate exactly `N` candidate points over the bounded region from `EngineConfig` (grid origin/bounds, max radius); deterministic given same origins + config
    - _Requirements: 5.1, 5.4_

  - [x] 4.2 Implement metric computation for candidates
    - Compute `Sum_Time` (Σ), `Max_Time` (max), and `Std_Dev` (σ) for each candidate from per-participant travel times; base all optimization on real travel times, never on geographic center
    - _Requirements: 5.2, 5.3_

  - [x] 4.3 Write property test for grid generation count and bounds
    - **Property 11: Grid generation count and bounds** — exactly `N` points generated, all within the configured search region
    - **Validates: Requirements 5.1**
    - jqwik, min 100 iterations; tagged per convention

  - [x] 4.4 Write property test for metric consistency
    - **Property 4: Metric consistency** — candidate `Sum_Time` = arithmetic sum, `Max_Time` = maximum, `Std_Dev` = standard deviation of per-participant times
    - **Validates: Requirements 5.2**
    - jqwik, min 100 iterations; tagged per convention

  - [x] 4.5 Write unit test demonstrating "not chosen by geographic center"
    - Example test showing a case where the selected point differs from the geographic centroid because real travel times dominate
    - _Requirements: 5.3_

- [x] 5. Implement the deterministic tie-breaker and strategy selectors
  - [x] 5.1 Implement TieBreaker with ε-tolerant comparison and total order
    - Implement `areEqual(a,b) := |a−b| < ε` (ε from config), composable ordered comparators, and a final centroid-distance comparator applied without ε to guarantee a total order
    - Provide a `Geographic_Centroid` computation (arithmetic mean of participant locations) used only as the final tiebreak
    - _Requirements: 2.5, 3.4, 4.7_

  - [x] 5.2 Implement FastestSelector
    - Key order `Σ → max → σ → distance-to-centroid`, selecting the single winner
    - _Requirements: 2.2, 2.3, 2.4, 2.5_

  - [x] 5.3 Implement MinimaxSelector
    - Key order `max → Σ → σ → distance-to-centroid`
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

  - [x] 5.4 Implement FairestSelector with feasibility restriction
    - Restrict to feasible set `Σ ≤ 1.15 · T*` (T* = min Σ), then key order `σ → Σ → max → distance-to-centroid`
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_

  - [x] 5.5 Write property test for Fastest optimality
    - **Property 5: Fastest optimality** — no candidate has `Sum_Time` strictly lower than the Fastest result beyond ε
    - **Validates: Requirements 2.2**
    - jqwik, min 100 iterations; tagged per convention

  - [x] 5.6 Write property test for Minimax optimality
    - **Property 6: Minimax optimality** — no candidate has `Max_Time` strictly lower than the Minimax result beyond ε
    - **Validates: Requirements 3.1**
    - jqwik, min 100 iterations; tagged per convention

  - [x] 5.7 Write property test for Fairest feasibility invariant
    - **Property 7: Fairest feasibility invariant** — Fairest result `Sum_Time` ≤ 1.15 · T* within ε
    - **Validates: Requirements 4.1, 4.2, 4.3**
    - jqwik, min 100 iterations; tagged per convention

  - [x] 5.8 Write property test for Fairest optimality within the feasible set
    - **Property 8: Fairest optimality within the feasible set** — no feasible candidate has `Std_Dev` strictly lower than the Fairest result beyond ε
    - **Validates: Requirements 4.4**
    - jqwik, min 100 iterations; tagged per convention

  - [x] 5.9 Write property test for tie-break totality
    - **Property 9: Tie-break totality** — each strategy's comparator chain ending in centroid distance (no ε) yields a single selected candidate with no unresolved tie
    - **Validates: Requirements 2.3, 2.4, 2.5, 3.2, 3.3, 3.4, 4.5, 4.6, 4.7**
    - jqwik, min 100 iterations; tagged per convention

- [x] 6. Implement the outlier detector
  - [x] 6.1 Implement OutlierDetector driven by the configured rule
    - Apply the config-driven rule (`t_i > k·median(t)` or `p90`) to per-participant travel times; return the set of outlier participant ids
    - _Requirements: 7.1_

  - [x] 6.2 Write property test for outlier detection matching the configured rule
    - **Property 14: Outlier detection matches the configured rule** — marked outliers exactly equal the configured rule applied to the time vector
    - **Validates: Requirements 7.1**
    - jqwik, min 100 iterations; tagged per convention

- [x] 7. Implement the RecommendationEngine orchestration
  - [x] 7.1 Implement RecommendationEngine.compute wiring all engine components
    - Generate candidates, route all participant×candidate pairs via injected `RoutingProvider`, compute metrics, run the three selectors, and return `Success`
    - On any routing failure, return `RoutingFailure` listing each affected participant and reason; never produce a `Success` that omits a participant
    - _Requirements: 2.2, 3.1, 4.4, 6.1, 6.3, 6.4, 8.1, 8.2, 8.3_

  - [x] 7.2 Add outlier dual-computation to the engine
    - When at least one outlier exists, compute all three strategies including and excluding the outlier(s), each with selected point and group average travel time; never drop an outlier silently
    - _Requirements: 7.2, 7.3, 7.5_

  - [x] 7.3 Write property test for determinism
    - **Property 10: Determinism** — running the computation twice with the same input/config/routing yields identical points and metrics for all three strategies
    - **Validates: Requirements 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 3.4, 4.4, 4.5, 4.6, 4.7**
    - jqwik, min 100 iterations; tagged per convention

  - [x] 7.4 Write property test for results completeness and shape
    - **Property 12: Results completeness and shape** — exactly one result per strategy, each with a valid in-range coordinate and per-participant times plus consistent Σ/max/σ
    - **Validates: Requirements 6.4, 8.1, 8.2, 8.3**
    - jqwik, min 100 iterations; tagged per convention

  - [x] 7.5 Write property test for routing-failure transparency
    - **Property 13: Routing-failure transparency** — when routing fails for ≥1 location, engine returns a routing-failure outcome identifying each affected location and reason, and never returns recommendations
    - **Validates: Requirements 6.1, 6.3, 6.4**
    - jqwik, min 100 iterations; tagged per convention

  - [x] 7.6 Write property test for outlier transparency
    - **Property 15: Outlier transparency** — with ≥1 outlier, output includes both including and excluding computations for all three strategies with points and group averages
    - **Validates: Requirements 7.2, 7.3, 7.5**
    - jqwik, min 100 iterations; tagged per convention

- [x] 8. Checkpoint - engine core complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Implement persistence (PostgreSQL + MeetingRepository adapter)
  - [x] 9.1 Implement the PostgreSQL schema and JPA entities
    - Create schema/migrations for MEETING (with `url_code`, `transport_mode`, timestamps, no user identity), PARTICIPANT, and RECOMMENDATION (with `excludes_outlier`, `per_participant_times` jsonb)
    - Map JPA entities mirroring the domain, keeping the domain free of persistence annotations
    - _Requirements: 9.1, 9.2, 9.6_

  - [x] 9.2 Implement the JPA MeetingRepository adapter and url_code generation
    - Implement `save`, `findByUrlCode`, `deleteByUrlCode`; generate a short, URL-safe, collision-checked `url_code` producing `/m/{code}` paths
    - _Requirements: 9.1, 9.2, 9.5_

  - [x] 9.3 Write property test for meeting persistence round-trip
    - **Property 17: Meeting persistence round-trip** — save then retrieve by `url_code` yields an equivalent meeting (count, names, locations, mode, recommendations)
    - **Validates: Requirements 9.1, 9.3**
    - jqwik, min 100 iterations; tagged per convention

  - [x] 9.4 Write integration tests for persistence CRUD
    - Test edit persistence, delete removal, and history retention against a test PostgreSQL instance
    - _Requirements: 9.4, 9.5, 9.6_

- [x] 10. Implement provider adapters (routing, geocoding, places stub)
  - [x] 10.1 Implement RoutingAdapter behind the RoutingProvider port
    - Call the chosen free/open-source routing service (per ADR-001) for whole-minute travel time; map timeouts/rate-limits to typed routing failures with clear reasons; keep API keys backend-only
    - _Requirements: 2.1, 5.2, 6.1, 11.4_

  - [x] 10.2 Implement GeocodingAdapter behind the GeocodingProvider port
    - Implement `autocomplete` and `resolve`; proxy the provider from the backend so keys never reach the frontend
    - _Requirements: 9.7, 11.4_

  - [x] 10.3 Implement PlacesProvider MVP stub
    - Provide a stubbed `PlacesProvider` implementation so post-MVP work is only writing an adapter
    - _Requirements: 12.3_

  - [x] 10.4 Write integration tests for geocoding autocomplete/resolve
    - Use a fake `GeocodingProvider` to test autocomplete and address resolution wiring
    - _Requirements: 9.7_

  - [x] 10.5 Write property test for coordinate and service-bounds validation
    - **Property 3: Coordinate and service-bounds validation invariant** — meeting accepted only if every location is in-range and within configured service bounds; else rejected identifying the offending participant
    - **Validates: Requirements 1.6, 1.7**
    - jqwik, min 100 iterations; tagged per convention

- [x] 11. Implement application use cases
  - [x] 11.1 Implement CreateMeeting, GetMeeting, EditMeeting, DeleteMeeting
    - Validate input, generate `Meeting_URL`, persist; resolve by `url_code` without identity control; apply edits; delete
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

  - [x] 11.2 Implement ComputeRecommendations use case
    - Drive the engine and persist produced recommendations (including outlier trade-off variants)
    - _Requirements: 2.2, 3.1, 4.4, 6.1, 7.2, 8.1_

  - [x] 11.3 Write unit tests for use-case behaviors and out-of-scope guards
    - Cover creator-decides outlier behavior (7.4), no venue booking (12.1), no invitations (12.2), no category filtering (12.3), no per-participant constraints (12.4)
    - _Requirements: 7.4, 12.1, 12.2, 12.3, 12.4_

- [x] 12. Implement the REST web adapter with cross-cutting concerns
  - [x] 12.1 Implement request validation and input sanitization
    - Sanitize inputs first, then validate at the web adapter; map failures to HTTP 400 with machine-readable codes identifying the offending field/participant
    - _Requirements: 1.2, 1.3, 1.5, 1.6, 1.7, 1.8_

  - [x] 12.2 Implement the Meeting controller endpoints under /api/v1
    - `POST /api/v1/meetings`, `GET/PUT/DELETE /api/v1/meetings/{code}`, `POST /api/v1/meetings/{code}/recommendations` (200 results or 422 routing-error), `GET /api/v1/geocode/autocomplete?q=`
    - Return actionable 422 message asking the Creator to correct/remove the location on routing failure
    - _Requirements: 6.2, 8.1, 8.2, 8.3, 9.1, 9.2, 9.4, 9.5, 9.7, 11.1_

  - [x] 12.3 Implement rate limiting and explicit CORS at the web adapter
    - Rate-limit filter keyed by client IP returning HTTP 429 when exceeded; re-assert CORS policy permitting only configured origins; ensure no provider keys appear in any response
    - _Requirements: 11.2, 11.3, 11.4_

  - [x] 12.4 Write integration tests for REST wiring, rate limiting, and CORS
    - Test `/api/v1` routing, 429 on rate-limit breach, CORS rejection for disallowed origins, and the actionable 422 routing-error message
    - _Requirements: 6.2, 11.1, 11.2, 11.3_

  - [x] 12.5 Write config/structure tests for city-agnosticism and key safety
    - Assert city-specific values come from `EngineConfig`, the 15% tolerance is a fixed constant (not a config field), and no provider key appears in responses or the frontend bundle
    - _Requirements: 4.3, 5.4, 11.4, 11.5_

- [x] 13. Checkpoint - backend API complete
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 14. Implement the frontend (Vue 3 + TS)
  - [x] 14.1 Implement AddressSearchBox with autocomplete
    - Per-participant address entry backed by the geocoding autocomplete endpoint; no map-click selection
    - _Requirements: 9.7, 9.8_

  - [x] 14.2 Implement StrategyComparisonView
    - Render the three strategy points with per-participant travel time, Σ, max, σ, each as an exact coordinate
    - _Requirements: 8.1, 8.2, 8.3_

  - [x] 14.3 Implement OutlierTradeoffPanel
    - Show include-vs-exclude comparison with group average travel times and let the Creator decide
    - _Requirements: 7.3, 7.4_

  - [x] 14.4 Implement i18n (ES/EN), LanguageSelector, and dark-mode ThemeProvider
    - ES/EN message catalogs, a language selector that switches interface language, and dark mode as the default and only MVP theme
    - _Requirements: 10.1, 10.3, 10.4, 10.5_

  - [ ] 14.5 Wire frontend to the backend API and meeting lifecycle
    - Connect create/view/edit/delete and recommendation requests to `/api/v1`; render routing-error prompts asking to correct/remove a location
    - _Requirements: 6.2, 9.1, 9.2, 9.3, 9.4, 9.5, 11.1_
    - NOTE (partial): Typed `/api/v1` client and create→compute→view flow + routing-error (422) rendering are implemented and wired in `App.vue`. BLOCKED on address→coordinate resolution: the backend exposes `GET /geocode/autocomplete` (returns `{description, placeId}` with no coordinate) but NO resolve endpoint, and `AddressSuggestion` carries no lat/lng. `MeetingForm` therefore cannot populate real participant coordinates from a chosen suggestion without fabricating them. Needs a backend `GET /api/v1/geocode/resolve?placeId=` (or `?q=`) returning a `Coordinate` (the `GeocodingProvider.resolve` port already exists; wire a controller endpoint), then finish `MeetingForm.onSelect` to store the resolved lat/lng.

  - [x] 14.6 Write frontend property test for pure metric formatting
    - fast-check (min 100 iterations) for pure formatting logic (e.g., minutes/metric rendering) integrated with Vitest
    - _Requirements: 8.2_

  - [x] 14.7 Write snapshot/DOM tests for UI, i18n, dark mode, and no-map-click
    - Vitest + Vue Test Utils: dark-mode default, desktop-first layout, ES/EN switching, and absence of map-click selection
    - _Requirements: 9.8, 10.1, 10.2, 10.3, 10.4, 10.5_

- [ ] 15. Final checkpoint - full stack integrated
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional (tests and the post-MVP places stub) and can be skipped for a faster MVP, but the engine property tests are strongly recommended since PBT is mandatory per project standards.
- The critical required path is the engine (tasks 2, 3.1, 3.2, 4.1, 4.2, 5.1–5.4, 6.1, 7.1, 7.2), persistence (9.1, 9.2), providers (10.1, 10.2), use cases (11.1, 11.2), API (12.1, 12.2, 12.3), and minimal UI (14.1–14.5).
- Each property test uses deterministic in-memory fakes for `RoutingProvider`/`GeocodingProvider`, runs a minimum of 100 iterations, and is tagged `Feature: meeting-recommendation-engine, Property {number}: {property_text}`.
- Property tests are placed close to the components they validate so errors surface early.
- All city-specific values are read from `EngineConfig`; `EFFICIENCY_TOLERANCE = 0.15` is a fixed domain constant.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["2.1", "2.2"] },
    { "id": 2, "tasks": ["2.3", "2.4", "2.5", "3.1", "3.2"] },
    { "id": 3, "tasks": ["3.3", "4.1", "4.2", "6.1"] },
    { "id": 4, "tasks": ["4.3", "4.4", "4.5", "5.1", "6.2", "10.5"] },
    { "id": 5, "tasks": ["5.2", "5.3", "5.4"] },
    { "id": 6, "tasks": ["5.5", "5.6", "5.7", "5.8", "5.9", "7.1"] },
    { "id": 7, "tasks": ["7.2", "7.3", "7.4", "7.5", "7.6"] },
    { "id": 8, "tasks": ["9.1", "10.1", "10.2", "10.3"] },
    { "id": 9, "tasks": ["9.2", "10.4"] },
    { "id": 10, "tasks": ["9.3", "9.4", "11.1", "11.2"] },
    { "id": 11, "tasks": ["11.3", "12.1"] },
    { "id": 12, "tasks": ["12.2"] },
    { "id": 13, "tasks": ["12.3"] },
    { "id": 14, "tasks": ["12.4", "12.5", "14.1", "14.2", "14.3", "14.4"] },
    { "id": 15, "tasks": ["14.5", "14.6", "14.7"] }
  ]
}
```
