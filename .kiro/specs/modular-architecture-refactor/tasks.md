# Implementation Plan: Modular Architecture Refactor

## Overview

This plan converts the design's incremental, behavior-preserving Migration Strategy (12 steps) into ordered coding tasks for an existing working app (Spring Boot Java 21 + Maven backend; Vue 3.5 + TypeScript strict + Vite/Vitest frontend). The refactor regroups code technical-layer-first → domain-module-first (`shared`, `meetings`, `locations`, `routing`), adds a `Route_Registry`, splits the frontend API layer into a shared `ApiClient` plus per-domain modules, adds additive display metadata, and adds a Leaflet map.

Guiding rules for every task:
- **Prefer moving/relocating existing code over rewriting.** Public signatures and computed results stay identical (R5.2, R5.3, R12).
- **Behavior-preservation gate after every step**: each backend move step ends by running `mvn verify`; each frontend step ends by running `vitest --run` + type-check (+ lint/build where noted). All must stay green and the app buildable (R12.2, R12.3).
- Preserved property tests move with import-only changes: `TieBreakTotalityPropertyTest`, `RoutingFailureTransparencyPropertyTest`, `ResultsCompletenessPropertyTest`, `OutlierTransparencyPropertyTest`, `OutlierDetectionPropertyTest`, `MinutesPropertyTest`, `MeetingInputParticipantCountPropertyTest`, `TransportModeValidationPropertyTest` (R13.6).
- Target ≥ 80% coverage. Property tests are tagged `Feature: modular-architecture-refactor, Property {n}: {property text}` and reference the design's Correctness Properties 1–8.

## Tasks

- [ ] 1. Establish target module package skeletons
  - Create `shared`, `meetings`, `locations`, `routing` package skeletons under `app.meethalfway` (with `domain`/`web`/`application`/`adapters` sub-packages per the design tree) so classes have destinations in later steps
  - Do not leave any empty module by the end of the migration; skeletons are populated in steps 2–5
  - Keep the app compiling and `mvn verify` green (no classes moved yet)
  - _Requirements: 2.1, 2.3_

- [ ] 2. Introduce Route_Registry and relocate shared/core API infra
  - [ ] 2.1 Add RouteRegistry and RouteRegistryVerifier, relocate core API infra into shared/web
    - Create `RouteRegistry` constants/enum in `shared/web` listing each API-exposing module → owned base path (`MEETINGS=/api/v1/meetings`, `LOCATIONS=/api/v1/geocode`, `HEALTH=/api/v1/health`), the single readable API-surface list (R3.1, R3.3)
    - Create `RouteRegistryVerifier` (startup `ApplicationRunner`/`@PostConstruct` bean) that fails application startup with a clear error naming the conflicting modules and base paths when two registered base paths overlap (equal or prefix) (R3.4)
    - Relocate `GlobalExceptionHandler`, `CorsConfigurer`, `RateLimitFilter`, `HealthController`, and shared error DTOs (`ErrorResponse`, `FieldErrorResponse`, `CoordinateResponse`) into `shared/web` / `shared/web/dto`
    - Wire `RouteRegistryVerifier` in `BeanConfiguration` (constructor injection only); keep existing error envelope, CORS, and rate-limit behavior unchanged (R6.2)
    - _Requirements: 3.1, 3.3, 3.4, 6.2, 6.5_

  - [ ]* 2.2 Write RouteRegistryVerifier overlap startup-failure test
    - Context-startup test registering two overlapping base paths; assert startup fails with an error naming the conflicting modules and base paths
    - _Requirements: 3.4, 13.4_

  - [ ]* 2.3 Write Route_Registry exposure integration test
    - Assert every base path declared in `RouteRegistry` is exposed exactly as declared (path + method) and wired to its controller
    - Assert existing external paths/methods are preserved (meetings CRUD, recommendations, geocode autocomplete/resolve, health)
    - _Requirements: 3.6, 13.4, 6.6_

  - [ ] 2.4 Run mvn verify
    - Run backend `mvn verify`; the full suite must stay green and the app buildable before proceeding
    - _Requirements: 12.2, 12.3_

- [ ] 3. Move meetings code into the meetings module
  - [ ] 3.1 Relocate meetings web, application, and persistence into meetings module
    - Move `MeetingController` (meetings + recommendations endpoints only), meeting/recommendation DTOs, and `WebMapper` into `meetings/web` and `meetings/web/dto`
    - Move use cases (`CreateMeeting`, `GetMeeting`, `EditMeeting`, `DeleteMeeting`, `ComputeRecommendations`, `UrlCodeGenerator`) into `meetings/application`
    - Move the persistence adapter (`JpaMeetingRepository`, entities, mapper, Spring Data interface) into `meetings/adapters/persistence`
    - Update `MeetingController` `@RequestMapping` to reference `RouteRegistry.MEETINGS`; no behavior change, no signature change (R2.2, R12)
    - Relocate `MeetingControllerTest` and `WebMapperTest` with import-only updates
    - _Requirements: 2.1, 2.2, 12.1, 12.3_

  - [ ] 3.2 Run mvn verify
    - Run backend `mvn verify`; suite green and app buildable before proceeding
    - _Requirements: 12.2, 12.3_

- [ ] 4. Split geocoding into the locations module
  - [ ] 4.1 Extract LocationController and relocate geocoding port/adapter into locations module
    - Create `LocationController` in `locations/web` serving `GET /geocode/autocomplete` and `GET /geocode/resolve`, extracted out of `MeetingController`; reference `RouteRegistry.LOCATIONS`
    - Relocate `GeocodingProvider` port (+ `GeocodeResult`, `AddressSuggestion`) into `locations/domain/port` and `GeocodingAdapter`/`HttpExchange`/`JdkHttpExchange` into `locations/adapters/geocoding`
    - Move `AddressSuggestionResponse` and geocoding response mapping into `locations/web/dto`
    - Place `PlacesProvider`/`StubPlacesProvider` under `locations` (port + stub adapter; exposes no route, not in RouteRegistry)
    - After this step no controller serves both meetings and geocoding concerns (R2.5); `LocationController` delegates to the `GeocodingProvider` port and maps via the mapper (thin controller)
    - Relocate/preserve `GeocodingEndpointTest` with import-only updates
    - _Requirements: 2.5, 2.1, 4.1, 12.1, 12.3_

  - [ ] 4.2 Run mvn verify
    - Run backend `mvn verify`; suite green and app buildable before proceeding
    - _Requirements: 12.2, 12.3_

- [ ] 5. Relocate engine/domain into meetings and shared without behavior change
  - [ ] 5.1 Move engine, cross-module primitives, and routing port/adapter into target modules
    - Move `RecommendationEngine` and all collaborators (`MeetingValidator`, `GridCandidateGenerator`/`CandidateGenerator`, `MetricCalculator`, `OutlierDetector`/`ConfiguredOutlierDetector`, `FastestSelector`, `MinimaxSelector`, `FairestSelector`, `StrategySelector`, `TieBreaker`, `GeographicCentroid`, `SearchRegion`) into `meetings/domain/engine`
    - Move cross-module primitives (`Coordinate`, `TransportMode`, `Minutes`, `ParticipantId`) into `shared/domain` (defined once, no per-module duplication)
    - Move routing port (`RoutingProvider`, `RouteResult`) into `routing/domain/port` and `OsrmRoutingAdapter` into `routing/adapters/routing`
    - Update `BeanConfiguration` wiring (constructor injection only); public signatures and computed results unchanged (R5.2, R5.3)
    - Update imports/packages of relocated unit tests and the preserved property tests (import-only changes)
    - _Requirements: 5.2, 5.3, 5.6, 2.4, 14.2, 12.3_

  - [ ]* 5.2 Verify preserved property tests pass after relocation (Properties 1–5 + preserved suite)
    - Confirm relocated `TieBreakTotalityPropertyTest`, `RoutingFailureTransparencyPropertyTest`, `ResultsCompletenessPropertyTest`, `OutlierTransparencyPropertyTest`, `OutlierDetectionPropertyTest`, `MinutesPropertyTest`, `MeetingInputParticipantCountPropertyTest`, `TransportModeValidationPropertyTest` pass with import-only changes
    - **Property 1: Output coordinates are within valid ranges** — every returned latitude in [-90,90], longitude in [-180,180]
    - **Property 2: Results are independent of participant input ordering** — permuting the participant list yields identical strategy results
    - **Property 3: Reported travel times are non-negative** — every per-participant time and each `sumTime`/`maxTime`/`stdDev` ≥ 0
    - **Property 4: Minimax minimizes the maximum participant travel time** — Minimax max per-participant time ≤ that of every other feasible candidate
    - **Property 5: Fastest minimizes aggregate travel time** — Fastest aggregate time ≤ that of every other feasible candidate
    - **Validates: Requirements 13.6**
    - _Requirements: 5.2, 13.6, 13.7_

  - [ ] 5.3 Run mvn verify
    - Run backend `mvn verify`; all existing unit and property tests green and app buildable
    - _Requirements: 12.2, 12.3_

- [ ] 6. Extend ArchUnit architecture rules for the new modules
  - [ ] 6.1 Add module-boundary, cycle, and controller-restriction rules to ArchitectureRulesTest
    - Keep existing inward-dependency, framework-free-domain, and no-field-injection rules
    - Add module-boundary rules for `meetings`/`locations`/`routing`/`shared`: forbid peer-module implementation dependencies (e.g. `meetings` must not depend on `locations` implementation); cross-module use only via `shared` or a domain port
    - Add no-cycles rule across packages/modules
    - Add controller-restriction rules: controllers must not reference adapter packages, `..engine..`, persistence query types, or concrete provider clients; controllers depend only on use cases, domain ports, and the mapper
    - Add no-catch-all-utility / duplicate-code guards where feasible
    - _Requirements: 1.5, 4.5, 4.6, 8.1, 8.2, 8.3, 8.4, 14.3_

  - [ ]* 6.2 Confirm rules fail on injected violations and pass on real code
    - Temporarily inject a boundary violation (e.g. a controller referencing an adapter, a cross-module impl import) and assert the rule fails naming the offender; remove the injection and assert the suite passes on real code
    - _Requirements: 1.5, 4.5, 8.2, 14.3_

  - [ ] 6.3 Run mvn verify
    - Run backend `mvn verify`; architecture suite and full suite green
    - _Requirements: 12.2, 12.3_

- [ ] 7. Add additive display metadata to backend DTOs
  - [ ] 7.1 Add candidateId/recommended and warnings; populate in WebMapper
    - Add `candidateId` (strategy key `"fastest"|"minimax"|"fairest"`) and `recommended` (boolean) to `StrategyResultResponse` (additive; no field removed/renamed)
    - Add `warnings: List<CoordinateWarningResponse>` to `RecommendationResponse` and create the new `CoordinateWarningResponse` record (`kind`, `reference`, `lat`, `lng`, `reason`); `warnings` defaults to empty list
    - Populate the new fields in `WebMapper`: set `candidateId` to the strategy key, mark exactly one point per strategy `recommended`; exclude out-of-range candidate points from `results` and add a warning; report out-of-range participant origins as warnings (never silently omit)
    - Additive and backward-compatible; document the additive change (R10.8, R12.4)
    - _Requirements: 10.2, 10.4, 10.6, 10.7, 10.8, 10.9, 7.8, 12.4_

  - [ ]* 7.2 Write WebMapper unit tests for display metadata
    - Assert `candidateId` equals strategy key, exactly one `recommended` per strategy, existing fields unchanged; valid warnings-empty case
    - _Requirements: 10.2, 10.3, 10.4, 7.8_

  - [ ]* 7.3 Write property test for display-metadata completeness
    - **Property 6: Display metadata is complete for every candidate point** — each returned strategy result carries lat, lng, total time, max single-participant time, std dev, a per-participant time for every included participant, a `candidateId` equal to its strategy key, and exactly one point per strategy marked `recommended`
    - **Validates: Requirements 10.2, 10.3, 10.4**
    - _Requirements: 10.2, 10.3, 10.4, 13.6_

  - [ ]* 7.4 Write property test for out-of-range candidate exclusion transparency
    - **Property 7: Out-of-range candidate points are excluded transparently** — given candidate coordinates outside valid ranges, the response excludes exactly those invalid candidates, retains all valid ones, and includes a warning identifying each excluded coordinate (never silently omit, never reject the whole response)
    - **Validates: Requirements 10.7**
    - _Requirements: 10.7, 13.6_

  - [ ]* 7.5 Write property test for out-of-range participant origin warnings
    - **Property 8: Out-of-range participant origins are reported as warnings** — given participant-origin coordinates outside valid ranges, the response includes a warning identifying each rather than silently omitting it
    - **Validates: Requirements 10.9**
    - _Requirements: 10.9, 13.6_

  - [ ] 7.6 Run mvn verify
    - Run backend `mvn verify`; full suite including new mapper/property tests green
    - _Requirements: 12.2, 12.3_

- [ ] 8. Add frontend ApiClient and domain client modules with re-export shim
  - [ ] 8.1 Mirror additive types and create shared ApiClient + domain clients
    - Mirror additive types in `types/Meeting.ts`: add `candidateId: StrategyKey` and `recommended: boolean` to `StrategyResult`; add new `CoordinateWarning` interface; add `warnings: CoordinateWarning[]` to `Recommendation` (additive only)
    - Create `api/client.ts` (`ApiClient`: base URL `/api/v1`, request transport, JSON (de)serialize, error normalization) and `api/errors.ts` preserving `ApiError` and `RoutingFailureError`
    - Create `api/meetings.ts` (create/get/edit/delete/computeRecommendations; `getMeeting` 404 → null; recommendations 422 → `RoutingFailureError`) and `api/geocoding.ts` (autocomplete; resolve 422 → null)
    - Turn `meetingApi.ts` into a thin re-export shim so existing imports keep working during migration
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.6, 10.2, 10.4, 12.1_

  - [ ]* 8.2 Write Vitest tests for ApiClient and domain client modules
    - Cover each preserved convention with mocked responses: `getMeeting` 404 → null, `resolve` 422 → null, recommendations 422 → `RoutingFailureError`, other non-2xx → `ApiError`, correct base URL and (de)serialization
    - _Requirements: 9.1, 9.4, 9.6, 13.5_

  - [ ] 8.3 Run vitest --run and type-check
    - Run frontend `vitest --run` and TypeScript strict type-check; both green
    - _Requirements: 12.2, 12.3_

- [ ] 9. Migrate App.vue and components to typed domain clients
  - [ ] 9.1 Switch imports to domain client modules and remove ad-hoc fetch
    - Migrate `App.vue` and components to import `api/meetings.ts` / `api/geocoding.ts` instead of `meetingApi.ts`
    - Ensure no ad-hoc `fetch` or client-bypassing HTTP call remains (R9.5)
    - Preserve `App.test.ts` (update imports only)
    - _Requirements: 9.5, 9.2, 12.1_

  - [ ] 9.2 Run vitest --run and type-check
    - Run frontend `vitest --run` and type-check; both green
    - _Requirements: 12.2, 12.3_

- [ ] 10. Add Leaflet MapView and composable
  - [ ] 10.1 Add Leaflet deps and implement useMeetingMap + MapView, slot into App.vue
    - Add `leaflet` and `@types/leaflet` dependencies (frontend only)
    - Implement `features/map/useMeetingMap.ts` composable and `features/map/MapView.vue` consuming backend data only (no recompute): render one candidate marker per strategy point with recommended-vs-alternative visual distinction; render 2–10 participant-origin markers (from `MeetingResponse.participants[].location`) distinct from candidates; auto-fit bounds over valid markers; popups showing `candidateId` + `sumTime`/`maxTime`/`stdDev`; empty state for zero candidates; exclude invalid coordinates with a non-blocking notice mirroring `warnings`; loading state; error state that clears stale markers
    - Apply the Vite `L.Icon.Default` marker-asset fix so markers render
    - Slot `MapView` into `App.vue` after `StrategyComparisonView`
    - _Requirements: 10.1, 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7, 11.8, 11.9, 11.10, 13.5_

  - [ ]* 10.2 Write Vitest @vue/test-utils tests for MapView
    - Assert candidate markers (one per candidate), recommended-vs-alternative distinction, 2–10 origin markers distinct from candidates, bounds fitting, popup content (`candidateId` + metrics)
    - At least one test each for loading, error, and empty states, plus invalid-coordinate exclusion notice
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7, 11.8, 11.9, 13.5_

  - [ ] 10.3 Run vitest --run and type-check
    - Run frontend `vitest --run` and type-check; both green
    - _Requirements: 12.2, 12.3_

- [ ] 11. Remove the meetingApi.ts shim and dead code
  - [ ] 11.1 Delete the flat shim and now-unused code
    - After all callers use the domain client modules and tests are green, delete `meetingApi.ts` and any now-unused/dead code (R14.3)
    - _Requirements: 14.3, 9.2, 12.1_

  - [ ] 11.2 Run vitest --run, type-check, and build
    - Run frontend `vitest --run`, type-check, and production build; all green
    - _Requirements: 12.2, 12.3_

- [ ] 12. Add OpenAPI description and contract test (optional/additive)
  - [ ]* 12.1 Add springdoc-openapi and assert documented contract matches endpoints
    - Add `springdoc-openapi-starter-webmvc-ui`; add a test asserting documented paths, DTO schemas, and field names match the exposed endpoints and DTO records
    - Additive and behavior-preserving; if the dependency is not added, R7.7 is satisfied vacuously (this task is optional)
    - _Requirements: 7.7_

- [ ] 13. Final behavior-preservation verification
  - [ ] 13.1 Run full backend and frontend gates and confirm preserved coverage
    - Run backend `mvn verify` and frontend `vitest --run`, type-check, lint, and build; all green and app buildable
    - Confirm the preserved property tests remain green and strategy coverage is maintained: each of the three strategies independently, epsilon-boundary ties at the Fairest tolerance, empty feasible Fairest set, outlier comparisons, exactly-2 and exactly-10 participants, both DRIVING and WALKING, and at least one unroutable location
    - _Requirements: 12.2, 12.3, 13.6, 13.7_

## Notes

- Tasks marked with `*` are optional (test-related or the optional OpenAPI task) and can be skipped for a faster MVP; core relocation/implementation tasks are never optional.
- Each task references specific requirements for traceability.
- `mvn verify` / `vitest --run` steps are the per-increment behavior-preservation gate (R12.2, R12.3); keeping them as explicit tasks enforces "always buildable after each step."
- Property tests reference the design's Correctness Properties 1–8; Properties 1–5 are preserved geospatial/mathematical invariants (verified via the relocated existing property suite), Properties 6–8 are the additive display-data invariants in `WebMapper`.
- Prefer moving/relocating existing code over rewriting; public signatures and computed results are unchanged across the refactor.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1"] },
    { "id": 1, "tasks": ["2.1"] },
    { "id": 2, "tasks": ["2.2", "2.3"] },
    { "id": 3, "tasks": ["2.4"] },
    { "id": 4, "tasks": ["3.1"] },
    { "id": 5, "tasks": ["3.2"] },
    { "id": 6, "tasks": ["4.1"] },
    { "id": 7, "tasks": ["4.2"] },
    { "id": 8, "tasks": ["5.1"] },
    { "id": 9, "tasks": ["5.2"] },
    { "id": 10, "tasks": ["5.3"] },
    { "id": 11, "tasks": ["6.1"] },
    { "id": 12, "tasks": ["6.2"] },
    { "id": 13, "tasks": ["6.3"] },
    { "id": 14, "tasks": ["7.1"] },
    { "id": 15, "tasks": ["7.2", "7.3", "7.4", "7.5"] },
    { "id": 16, "tasks": ["7.6"] },
    { "id": 17, "tasks": ["8.1"] },
    { "id": 18, "tasks": ["8.2"] },
    { "id": 19, "tasks": ["8.3"] },
    { "id": 20, "tasks": ["9.1"] },
    { "id": 21, "tasks": ["9.2"] },
    { "id": 22, "tasks": ["10.1"] },
    { "id": 23, "tasks": ["10.2"] },
    { "id": 24, "tasks": ["10.3"] },
    { "id": 25, "tasks": ["11.1"] },
    { "id": 26, "tasks": ["11.2"] },
    { "id": 27, "tasks": ["12.1"] },
    { "id": 28, "tasks": ["13.1"] }
  ]
}
```
