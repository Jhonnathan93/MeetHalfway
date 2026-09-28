# Requirements Document

## Introduction

This feature is an **incremental architectural refactor** of the existing MeetHalfway application plus one **new user-facing capability**: a Leaflet-based map visualization of computed meeting points.

The refactor reorganizes both the backend (Spring Boot, Java 21) and the frontend (Vue 3.5 + TypeScript strict) from a technical-layer-first layout into a **domain-module-first** layout inspired by modular MVC. The backend already follows Clean/Hexagonal architecture with an ArchUnit test enforcing inward dependency direction; controllers are already thin and delegate through use cases via a web mapper. Those strengths are preserved. The primary structural problems this refactor addresses are:

- A **single `MeetingController`** mixes two distinct concerns (meeting CRUD/recommendations and geocoding).
- Code is grouped by **global technical folders** (`adapters/web`, `application`, `domain`) rather than by **domain module** (meetings, locations/geocoding, routing).
- There is **no central, readable place** that shows which API modules and routes are exposed.
- The **frontend API layer** is a single flat module (`meetingApi.ts`) rather than a client split by domain.

The new map capability adds visualization only: the backend continues to calculate candidate/meeting points and the frontend renders them on an interactive map. The refactor is **behavior-preserving** — existing endpoints, calculations, and passing tests keep working unless a change is explicitly documented as making them obsolete.

This document reconciles the user's Django-inspired vocabulary ("urls", "controllers", "apps", "BaseApi") with idiomatic Spring/Vue conventions. Requirements are stated in **framework-appropriate** terms; where the user's literal request conflicts with strong Spring/Vue conventions, the requirement mandates an explicit, documented design decision rather than a literal port.

### Scope boundaries (non-goals)

The following are explicitly **out of scope** and MUST NOT be introduced: authentication, invitations, microservices, message queues, Redis or any caching layer, event-driven architecture, CQRS, Kubernetes, additional databases, AI/LLM features, speculative repository abstractions, complex/advanced GIS, and a full UI redesign.

## Glossary

- **System**: The MeetHalfway application as a whole (backend plus frontend).
- **Backend**: The Spring Boot service under package root `app.meethalfway`, exposing the REST API.
- **Frontend**: The Vue 3.5 + TypeScript client application.
- **Domain_Module**: A cohesive, business-oriented grouping of code owning a single bounded concern (for example `meetings`, `locations`/geocoding, `routing`). It contrasts with a technical layer (for example "all controllers").
- **Controller**: A backend HTTP entry point (Spring `@RestController`) responsible only for HTTP concerns: request binding, validation triggering, delegation to a use case, and response/status mapping.
- **Use_Case**: An application-layer service that orchestrates domain logic for one operation (for example `CreateMeeting`, `ComputeRecommendations`).
- **Domain_Utility**: A focused domain class encapsulating one cohesive responsibility (for example coordinate handling, scoring, validation, geographic computation). It is not a generic "utils" dumping ground.
- **Provider_Port**: A domain-owned interface abstracting an external integration (`GeocodingProvider`, `RoutingProvider`, `PlacesProvider`, `MeetingRepository`).
- **Adapter**: A concrete implementation of a Provider_Port or an HTTP/persistence boundary component, residing at the infrastructure edge.
- **Route_Registry**: The single, readable composition point that documents which Domain_Modules expose which API routes under `/api/v1`.
- **DTO**: A Data Transfer Object (request/response schema) that forms the explicit API contract, distinct from internal domain models.
- **Architecture_Test**: The automated ArchUnit-based test suite (currently `ArchitectureRulesTest`) that mechanically enforces dependency rules.
- **API_Client**: The frontend centralized layer responsible for HTTP transport, base URL, (de)serialization, and shared error handling.
- **Domain_Client_Module**: A per-domain frontend module (for example a meetings client, a geocoding client) built on top of the shared API_Client.
- **Map_View**: The frontend Leaflet component that renders candidate/meeting points and participant origins.
- **Candidate_Point**: A computed geographic point returned by a strategy, carrying latitude, longitude, an identifier, and score/metric metadata.
- **Recommended_Point**: The Candidate_Point selected as the best/recommended result for a strategy, as distinguished from alternatives.
- **Strategy**: One of the three optimization strategies: Fastest, Minimax, Fairest.
- **Participant_Origin**: A participant's input location (latitude/longitude) used as a routing origin.
- **Behavior_Preservation**: The hard constraint that externally observable behavior (endpoints, response contracts, calculation results) remains unchanged across the refactor unless a change is explicitly documented.
- **Property_Test**: A property-based test asserting invariants across generated inputs.

## Requirements

### Requirement 1: Strict layer separation across frontend, API, services, and domain

**User Story:** As a developer, I want strict separation between the frontend, the API/controller layer, the services/use-case layer, and the models/domain/providers layer, so that each layer changes independently and communication stays explicit.

#### Acceptance Criteria

1. THE System SHALL organize code into four separable responsibilities: Frontend presentation, Backend Controller (HTTP), Use_Case services (application), and domain models with Provider_Ports.
2. THE Backend SHALL expose functionality only through the REST API under `/api/v1` and SHALL NOT reference any Frontend source module.
3. THE Frontend SHALL communicate with the Backend only through HTTP requests to the `/api/v1` REST API.
4. IF any Frontend source module imports, references, or bundles Backend implementation code, THEN THE Frontend build SHALL fail with an error identifying the offending import path.
5. THE Architecture_Test SHALL enforce that the Backend Controller layer depends on Use_Case services and that Use_Case services depend only on domain models and Provider_Ports, and SHALL fail with an error identifying the offending class when an inner layer depends on an outer layer.

### Requirement 2: Backend organized by domain module

**User Story:** As a developer, I want the backend organized by domain module rather than by global technical folders, so that I can locate and change one business concern in one place.

#### Acceptance Criteria

1. THE Backend SHALL group code by Domain_Module for meetings, locations (geocoding), and routing.
2. WHERE a Domain_Module exposes HTTP endpoints, THE Backend SHALL place that module's Controller, request/response DTOs, Use_Cases, and module-specific domain code within that module's package boundary.
3. THE Backend SHALL ensure every Domain_Module contains at least one production class and SHALL NOT create an empty placeholder module.
4. THE Backend SHALL define each shared cross-module domain primitive (`Coordinate`, `TransportMode`, `Minutes`) in exactly one shared domain location and SHALL NOT duplicate any of these primitives per module.
5. THE Backend SHALL serve the meetings concern and the geocoding/locations concern through separate Controllers so that no single Controller serves both the `/meetings*` endpoints and the `/geocode/*` endpoints.

### Requirement 3: Centralized route composition with per-module ownership

**User Story:** As a developer, I want a single readable place that shows which API modules and routes are exposed, with each domain module owning its own route definitions, so that I can understand the API surface at a glance while keeping module autonomy.

#### Acceptance Criteria

1. THE Backend SHALL provide a Route_Registry that lists, in one location, every Domain_Module that exposes API routes together with the base path that module owns.
2. THE Backend SHALL keep each Domain_Module the owner of its concrete route definitions (path segments and HTTP methods) within that module's package.
3. THE Backend SHALL expose all API routes under the `/api/v1` prefix using kebab-case path segments.
4. IF two or more Domain_Modules declare overlapping base paths in the Route_Registry, THEN THE Backend SHALL fail at application startup with an error identifying the conflicting modules and base paths.
5. WHEN a new Domain_Module with API routes is added, THE Route_Registry SHALL list that module without requiring edits to any unrelated module's route definitions.
6. THE Backend SHALL preserve the existing externally observable route paths and HTTP methods (meetings CRUD, recommendations, geocoding autocomplete and resolve, health) as defined by Behavior_Preservation.

### Requirement 4: Thin controllers limited to HTTP concerns

**User Story:** As a developer, I want controllers to remain thin and handle only HTTP concerns, so that business rules stay testable and framework-independent.

#### Acceptance Criteria

1. THE Backend Controller layer SHALL be limited to the following HTTP concerns and no others: request payload binding, invocation of declarative input validation, delegation to a Use_Case, and mapping of Use_Case results to response payloads and HTTP status codes.
2. THE Backend Controller layer SHALL NOT contain business rules, scoring logic, meeting-point algorithms, routing algorithms, geographic algorithms, persistence query logic, or direct external-provider invocation.
3. WHEN a Controller method handles a single inbound request, THE Controller SHALL delegate that operation's business logic to exactly one Use_Case invocation and SHALL NOT invoke a second Use_Case for the same operation.
4. WHEN a Controller obtains a result from a Use_Case, THE Controller SHALL transform that result into the response payload exclusively through the WebMapper rather than constructing domain-to-response transformations inline.
5. IF a Controller class references a Provider_Port implementation, an algorithm class, a persistence query, or any external-provider client directly, THEN THE Architecture_Test SHALL fail and SHALL report an error identifying the violating Controller class and the referenced disallowed type.
6. IF a Controller method body contains more than one statement that is neither request binding, validation invocation, WebMapper mapping, nor a single Use_Case delegation, THEN THE Architecture_Test SHALL fail and SHALL report an error identifying the violating Controller method.

### Requirement 5: Business logic in services and focused domain utilities

**User Story:** As a developer, I want business logic to live in use-case services and focused domain utilities, so that logic is cohesive, reusable, and not scattered into god services or generic utils.

#### Acceptance Criteria

1. THE Backend SHALL place all business logic in Use_Case services or Domain_Utility classes, where each Domain_Utility addresses exactly one of the following named concerns: coordinate computation, scoring, validation, or geographic computation.
2. THE Backend SHALL preserve the observable behavior of every existing domain and application component (DefaultRecommendationEngine, FastestSelector, MinimaxSelector, FairestSelector, GridCandidateGenerator, MetricCalculator, GeographicCentroid, OutlierDetector, TieBreaker, MeetingValidator, and the existing use-case services) such that, for identical inputs, each relocated component produces outputs identical to those it produced before relocation.
3. WHEN an existing domain or application component is relocated, THE Backend SHALL move it into a single Domain_Module or shared domain location without altering its public method signatures or its computed results.
4. IF any class would hold responsibilities spanning more than one of the named concerns in Criterion 1, THEN THE Backend SHALL reject that structure and split the responsibilities into separate single-concern classes, so that no service or utility concentrates two or more unrelated responsibilities.
5. IF a class is named or defined as a generic catch-all utility, THEN THE Backend SHALL reject it and require each Domain_Utility to declare exactly one named responsibility reflected in its class name.
6. WHERE logic is used by two or more Domain_Modules, THE Backend SHALL define that logic in exactly one location and reference it from each consumer, so that no two modules contain duplicate copies of the same logic.

### Requirement 6: Explicit decision on a base controller abstraction

**User Story:** As a developer, I want an explicit, justified decision about whether to introduce a shared base controller abstraction, so that we avoid inheritance that conflicts with Spring conventions and avoid premature abstraction.

#### Acceptance Criteria

1. THE design phase SHALL produce a single documented decision record that states one of exactly two outcomes—"adopt base controller abstraction" or "reject base controller abstraction"—and that includes a written justification of at least one sentence referencing the criteria in this requirement.
2. WHERE cross-cutting HTTP concerns (error handling, consistent error responses, common HTTP status mapping) are shared across two or more controllers, THE Backend SHALL implement them using idiomatic Spring mechanisms (`@RestControllerAdvice` global exception handling and `ResponseEntity` conventions) rather than a shared base-controller inheritance layer.
3. IF the documented decision adopts a base controller abstraction, THEN THE design decision record SHALL identify at least two distinct concrete purposes the abstraction serves and SHALL state that none of those purposes duplicate behavior already provided by the existing `@RestControllerAdvice` global exception handler, CORS configuration, or rate-limit filter.
4. IF a proposed base controller abstraction serves fewer than two distinct concrete purposes, or duplicates behavior already provided by the existing global exception handler, CORS configuration, or rate-limit filter, THEN THE Backend SHALL NOT introduce that abstraction and THE decision record SHALL record the rejection with the specific reason.
5. THE design decision record SHALL reference the existing GlobalExceptionHandler (`@RestControllerAdvice`), CORS configuration, and rate-limit filter components by name and SHALL describe how each shared cross-cutting concern is handled without a base controller.

### Requirement 7: Consistent REST API contracts

**User Story:** As an API consumer, I want consistent REST contracts for naming, methods, status codes, schemas, errors, and versioning, so that the API is predictable and safe to integrate against.

#### Acceptance Criteria

1. THE Backend SHALL expose all endpoints under the base path `/api/v1`, using kebab-case for every path segment and camelCase for every JSON field name in all request and response bodies.
2. THE Backend SHALL define explicit request and response DTO records as the API contract, and SHALL NOT serialize internal domain models directly as the API schema.
3. IF a request body fails boundary validation, THEN THE Backend SHALL return HTTP 400 with an error response body that identifies each failing field by name and states the validation rule violated, without exposing stack traces or provider internals.
4. IF a domain rule is violated, including participant count outside the inclusive range of 2 to 10 or a travel mode other than DRIVING or WALKING, THEN THE Backend SHALL return HTTP 400 with an error response body that names the violated domain rule and the offending value.
5. IF an unexpected server error occurs, THEN THE Backend SHALL return HTTP 500 with an error response body that excludes secrets, stack traces, exception class names, and external-provider internal details.
6. IF routing cannot be calculated for one or more participants, THEN THE Backend SHALL return HTTP 422 with an error response body that lists each affected participant location and its specific failure reason, and SHALL NOT omit any affected participant from the response.
7. WHERE the backend toolchain supports OpenAPI generation, THE Backend SHALL provide an OpenAPI description whose documented paths, DTO schemas, and field names match the exposed endpoints and DTO records.
8. THE Backend SHALL preserve the existing response schemas for all current endpoints, including each strategy result point with latitude and longitude fields, participant locations, outlier trade-off data, and routing-failure error shapes.

### Requirement 8: Enforced dependency direction and no circular dependencies

**User Story:** As a developer, I want dependency direction enforced automatically with no circular dependencies, so that architectural boundaries do not erode over time.

#### Acceptance Criteria

1. THE Architecture_Test SHALL enforce that domain code depends on no outer layer, that application code depends only on domain code, and that adapters and configuration reside at the edges, and SHALL fail with an error identifying the offending class when an inward dependency rule is violated.
2. THE Architecture_Test SHALL enforce the Domain_Module boundaries and SHALL fail with an error identifying the offending import when a module declares a forbidden dependency on another module that must remain independent.
3. IF a circular dependency exists between packages or Domain_Modules, THEN THE Architecture_Test SHALL fail with an error identifying the classes participating in the cycle.
4. IF the domain layer declares a dependency on Spring, JPA, servlet, or Hibernate types, THEN THE Architecture_Test SHALL fail with an error identifying the offending class and disallowed type.
5. THE System SHALL document the allowed and forbidden dependency directions between Frontend, Controller, Use_Case, domain, Provider_Ports, and Adapters.
6. IF Frontend code imports Backend implementation code, THEN THE Frontend build SHALL fail with an error identifying the offending import path.

### Requirement 9: Frontend centralized API client split by domain

**User Story:** As a frontend developer, I want a centralized API layer split by domain with typed operations, so that components consume typed operations without duplicating transport, base URL, or error-handling logic.

#### Acceptance Criteria

1. THE Frontend SHALL provide a single shared API_Client responsible for base URL configuration, request transport, JSON serialization and deserialization, and common error handling.
2. THE Frontend SHALL split domain operations into Domain_Client_Modules for meetings, geocoding, and recommendations built on the shared API_Client, replacing the single flat `meetingApi.ts` module.
3. THE Frontend SHALL expose typed operations whose request and response types mirror the Backend DTO contracts.
4. THE Frontend SHALL surface transport errors, validation errors, domain errors, and routing failures through the shared API_Client as typed, distinguishable errors, preserving the existing `ApiError` and `RoutingFailureError` classes.
5. THE Frontend components SHALL consume Domain_Client_Module typed operations and SHALL NOT issue `fetch` or other HTTP calls that bypass the API_Client.
6. THE Frontend SHALL keep the API base path as `/api/v1` and SHALL preserve the existing client conventions defined by Behavior_Preservation, including the getMeeting 404-to-null convention and the resolve 422 routing-failure convention.

### Requirement 10: Backend exposes display-ready candidate/meeting point data

**User Story:** As a frontend developer, I want the backend to return candidate/meeting points with the metadata needed to display them, so that the frontend can visualize results without recomputing anything.

#### Acceptance Criteria

1. THE Backend SHALL calculate all Candidate_Points and Recommended_Points, and THE Frontend SHALL NOT perform meeting-point, routing, or scoring computation.
2. WHEN the Backend returns a recommendation response, THE Backend SHALL include for each Candidate_Point a latitude, a longitude, a candidate identifier that uniquely maps to its Strategy (Fastest, Minimax, or Fairest), the total travel time, the maximum single-participant travel time, and the standard deviation of participant travel times.
3. WHEN the Backend returns a recommendation response, THE Backend SHALL include, for each Candidate_Point, the per-participant travel time for every included participant.
4. THE Backend SHALL mark exactly one Candidate_Point per Strategy as the Recommended_Point using an explicit recommended indicator so the Frontend can distinguish it from alternatives.
5. WHEN participant coordinates are present in the meeting, THE Backend SHALL include each Participant_Origin latitude and longitude in the response.
6. THE Backend SHALL return every latitude in the inclusive range -90 to 90 and every longitude in the inclusive range -180 to 180.
7. IF any computed Candidate_Point, Recommended_Point, or Participant_Origin coordinate falls outside the inclusive latitude range -90 to 90 or longitude range -180 to 180, THEN THE Backend SHALL reject the response with an error indicating the specific out-of-range coordinate and SHALL NOT return the invalid coordinate silently.
8. THE Backend SHALL preserve the existing recommendation response contract as defined by Behavior_Preservation and SHALL add the display metadata required by this requirement only as additional fields, without removing or altering existing fields.

### Requirement 11: Leaflet map visualization of results

**User Story:** As a user, I want to see the recommended meeting point and alternatives on an interactive map, so that I can understand where to meet relative to everyone's origin.

#### Acceptance Criteria

1. WHEN a recommendation response contains at least one Candidate_Point, THE Map_View SHALL render an interactive Leaflet map showing exactly one marker for each Candidate_Point in the response.
2. THE Map_View SHALL render the Recommended_Point marker with a visual treatment distinct from alternative Candidate_Point markers.
3. WHEN Candidate_Points are displayed, THE Map_View SHALL fit the map bounds to include every displayed marker.
4. WHEN a user selects a Candidate_Point marker, THE Map_View SHALL display that candidate's identifier and its score/metric metadata.
5. WHEN Participant_Origin coordinates are present in the response, THE Map_View SHALL render one origin marker per participant with a visual treatment distinct from Candidate_Point markers, so that a meeting of 2 to 10 participants renders between 2 and 10 origin markers.
6. IF a recommendation response contains zero Candidate_Points, THEN THE Map_View SHALL display an explicit empty-state message and SHALL NOT render meeting markers.
7. IF a Candidate_Point or Participant_Origin has a latitude outside the inclusive range -90 to 90 or a longitude outside the inclusive range -180 to 180, THEN THE Map_View SHALL exclude that point from rendering and SHALL display a non-blocking notice identifying the excluded point.
8. WHILE a recommendation request is in progress, THE Map_View SHALL display a loading state.
9. IF a recommendation request fails, THEN THE Map_View SHALL display an error state describing the failure and SHALL NOT display markers from a previous response as current results.
10. THE Map_View SHALL provide only marker rendering, bounds fitting, and marker inspection, and SHALL NOT introduce advanced GIS features.

### Requirement 12: Behavior preservation throughout the refactor

**User Story:** As a maintainer, I want existing behavior preserved throughout the refactor, so that users experience no regression and the application is never left needlessly broken.

#### Acceptance Criteria

1. THE System SHALL treat Behavior_Preservation as a hard constraint so that existing endpoints, HTTP methods, request and response contracts, and calculation results remain unchanged unless a change is explicitly documented.
2. WHEN the refactor is applied as an increment, THE System SHALL keep the application in a buildable and runnable state at the end of that increment.
3. THE Backend SHALL keep every currently passing automated test passing, unless a specific test is explicitly documented as obsolete due to a documented contract change.
4. IF a change alters an externally observable contract, THEN THE System SHALL document the change and its rationale before that change is considered complete.
5. THE System SHALL preserve the existing project standards: `/api/v1` kebab-case paths with camelCase JSON fields, Provider_Port abstractions, no secrets in the Frontend, English-only code and documentation, Spanish and English UI with an explicit language selector, dark mode, meetings of 2 to 10 participants, a single shared travel mode per meeting, and reporting rather than silently dropping an unroutable participant.

### Requirement 13: Testability at layer boundaries

**User Story:** As a developer, I want each layer boundary to be testable, so that regressions are caught at the appropriate level and invariants are guaranteed.

#### Acceptance Criteria

1. THE System SHALL support unit tests that exercise Use_Case services, scoring logic, and geographic/coordinate Domain_Utilities with all HTTP and persistence dependencies replaced by test doubles, such that no test in this set opens a network socket or database connection.
2. WHEN a controller/API test submits a request, THE System SHALL support verifying request validation, response HTTP status code, success response contract, and error response contract, and SHALL support verifying that the Controller delegates to its Use_Case exactly once per valid request.
3. IF a controller/API test submits a request that fails input validation, THEN THE System SHALL support asserting that the request is rejected with a client-error status, that an error response indicating the specific validation failure is returned, and that no Use_Case invocation occurs.
4. THE System SHALL support integration tests verifying that every route declared in the Route_Registry is exposed exactly as declared (path, method) and that each exposed route is wired to its corresponding service.
5. THE Frontend SHALL support tests for the API_Client and Domain_Client_Modules, Map_View rendering, candidate rendering, and each of the loading, error, and empty display states, such that every one of these three states has at least one test asserting its rendered output.
6. THE System SHALL preserve all existing Property_Tests (TieBreakTotality, RoutingFailureTransparency, ResultsCompleteness, OutlierTransparency, OutlierDetection, MinutesProperty, MeetingInputParticipantCount, TransportModeValidation) and SHALL maintain Property_Tests for the following geospatial/mathematical invariants: results are independent of participant input ordering; every output coordinate has latitude within -90 to 90 degrees inclusive and longitude within -180 to 180 degrees inclusive; every reported travel time is greater than or equal to zero; the Minimax selection has a maximum per-participant travel time less than or equal to that of every other feasible Candidate_Point; and the Fastest selection has an aggregate travel time less than or equal to that of every other feasible Candidate_Point.
7. THE System SHALL preserve strategy test coverage for the following cases: each of the three strategies tested independently; epsilon-boundary ties at the configured Fairest efficiency tolerance; an empty feasible Fairest set; outlier comparison cases; meetings with exactly 2 and exactly 10 participants; both DRIVING and WALKING travel modes; and at least one unroutable location.

### Requirement 14: Code quality and boundary discipline

**User Story:** As a maintainer, I want the refactor to uphold code-quality principles, so that the codebase stays cohesive, readable, and free of architectural erosion.

#### Acceptance Criteria

1. THE System SHALL define each module, service, and Domain_Utility with exactly one responsibility reflected in its name.
2. THE System SHALL apply dependency inversion for external integrations through Provider_Ports so that a concrete adapter can be replaced without changing domain or application code.
3. IF duplicated logic, dead code, a circular dependency, or a generic catch-all utility class is present in the Backend, THEN THE Architecture_Test SHALL fail with an error identifying the offending class.
4. THE System SHALL keep business logic independent of HTTP and SHALL place framework code at the infrastructure and API boundary.
5. THE System SHALL introduce an abstraction only where it serves a concrete, documented use, and SHALL NOT introduce an abstraction that serves fewer than one concrete use.
6. THE System SHALL use descriptive English naming for all modules, classes, methods, and variables, following Java conventions on the Backend and TypeScript conventions on the Frontend.
