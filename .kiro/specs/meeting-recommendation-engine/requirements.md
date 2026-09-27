# Requirements Document

## Introduction

MeetHalfway helps a group find the fairest place to meet based on **actual travel time**, not the geographic center of the group. Given a set of participant locations and a shared transport mode, the engine computes recommended meeting points using three distinct optimization strategies (Fastest, Minimax, and Fairest) and presents them so the organizer can choose the trade-off that best fits the group.

The MVP targets Medellín / Valle de Aburrá, but the design is **city-agnostic**: any Medellín-specific value (bounds, defaults, provider settings) lives in configuration, never hard-coded into the optimization logic. The MVP supports groups of 2 to 10 people, has no authentication, and supports two transport modes (Car/driving and Walking), with all participants in a single meeting using the same mode.

This document defines the functional requirements for the recommendation logic and the meeting-management behavior around it: accepting and validating participant input, computing the three strategies, applying deterministic tie-breaking, handling routing errors and outliers transparently, formatting results, managing meetings (CRUD without authentication), and the cross-cutting behaviors of the system (input validation, rate limiting, CORS, API-key handling, internationalization, and dark-mode UI). It does not prescribe implementation technology or specific third-party providers.

Some values are intentionally left open and are documented in the "Open Questions and Assumptions" section rather than being invented here.

## Glossary

- **System**: The MeetHalfway application as a whole (backend engine plus the API and UI behaviors it exposes).
- **Engine**: The Meeting Recommendation Engine, the component that computes meeting points from participant locations.
- **Meeting**: A single named coordination unit created by a Creator, containing the set of Participant Locations, a shared Transport_Mode, and computed Recommendations. Persisted with a shareable Meeting_URL.
- **Creator**: The person who creates and edits a Meeting. In the MVP the Creator enters all Participant Locations directly; there is no per-participant invitation and no identity control.
- **Participant**: A person attending the Meeting, represented by a Participant_Location and an optional display name.
- **Participant_Location**: A geographic coordinate (latitude and longitude) representing where a Participant begins travel, resolved from an address via geocoding.
- **Transport_Mode**: The single travel method shared by all Participants in a Meeting, one of "driving" (Car) or "walking".
- **Travel_Time**: The estimated real travel time in minutes (t_i) for Participant i to reach a Candidate_Point using the Meeting's Transport_Mode.
- **Candidate_Point**: A geographic point evaluated by the Engine as a possible meeting location.
- **Grid_Search**: The MVP evaluation approach that generates N Candidate_Points (a grid or road intersections), computes metrics for each, and selects results per strategy.
- **Sum_Time**: The total of all Participants' Travel_Times to a Candidate_Point, Σ t_i.
- **Max_Time**: The largest individual Travel_Time to a Candidate_Point, max(t_i).
- **Std_Dev**: The standard deviation of the Participants' Travel_Times to a Candidate_Point, σ(t).
- **Fastest_Strategy**: The strategy that minimizes Sum_Time (min Σ t_i).
- **Minimax_Strategy**: The strategy that minimizes Max_Time (min max(t_i)).
- **Fairest_Strategy**: The constrained strategy that, among Candidate_Points whose Sum_Time is within the Efficiency_Tolerance of the Fastest optimum, minimizes Std_Dev.
- **Fastest_Optimum (T\*)**: The minimum achievable Sum_Time across all Candidate_Points, T\* = min Σ t_i.
- **Efficiency_Tolerance**: The fixed 15% allowance (not user-configurable) defining the feasible set for the Fairest_Strategy: a Candidate_Point is feasible when Σ t_i(p) ≤ 1.15 · T\*.
- **Geographic_Centroid**: The arithmetic mean of all Participant_Locations, used only as the final deterministic tie-breaker.
- **Equality_Threshold (ε)**: The float-comparison tolerance used to decide metric ties, are_equal(a, b) := |a − b| < ε, with ε ≈ 0.5 minute.
- **Outlier**: A Participant whose Travel_Time is exaggeratedly larger than the rest of the group per the Outlier detection rule.
- **Recommendation**: The result for one strategy, consisting of one Candidate_Point plus its metrics (per-Participant Travel_Time, Sum_Time, Max_Time, Std_Dev).
- **Meeting_URL**: A short shareable URL (for example meethalfway.app/m/8K3F2) that grants access to view and edit a Meeting without identity control.
- **Configuration**: The set of externalized values (geographic bounds, defaults, provider keys, limits) that adapt the System to a city or environment without changing the optimization logic.

## Requirements

### Requirement 1: Accept and Validate Meeting Input

**User Story:** As a Creator, I want to submit the participants and their locations for a meeting, so that the engine can compute fair meeting points for the group.

#### Acceptance Criteria

1. WHEN a Meeting is submitted with a Participant count from 2 to 10 inclusive, THE Engine SHALL accept the Meeting for processing.
2. IF a Meeting is submitted with fewer than 2 Participants, THEN THE Engine SHALL reject the Meeting and return a validation error identifying the minimum Participant count.
3. IF a Meeting is submitted with more than 10 Participants, THEN THE Engine SHALL reject the Meeting and return a validation error identifying the maximum Participant count.
4. THE Meeting SHALL specify exactly one Transport_Mode, either "driving" or "walking", that applies to all Participants.
5. IF a Meeting is submitted with a Transport_Mode other than "driving" or "walking", THEN THE Engine SHALL reject the Meeting and return a validation error identifying the supported Transport_Modes.
6. IF a Participant is submitted with a Participant_Location outside the valid latitude range of -90 to 90 degrees or the valid longitude range of -180 to 180 degrees, THEN THE Engine SHALL reject the Meeting and return a validation error identifying the invalid Participant.
7. WHERE a Participant_Location falls outside the configured service bounds, THE Engine SHALL reject the Meeting and return a validation error identifying the out-of-bounds Participant.
8. THE System SHALL sanitize all input values before processing to remove or neutralize unsafe content.

### Requirement 2: Compute the Fastest Strategy

**User Story:** As a Creator, I want a meeting point that minimizes the group's total travel time, so that the group spends the least combined time traveling.

#### Acceptance Criteria

1. WHEN a valid Meeting is accepted, THE Engine SHALL compute Travel_Time for each Participant to each Candidate_Point using the Meeting's Transport_Mode.
2. WHEN Travel_Times are computed, THE Engine SHALL select as the Fastest_Strategy result the Candidate_Point that minimizes Sum_Time (min Σ t_i).
3. WHERE two or more Candidate_Points have equal Sum_Time within the Equality_Threshold, THE Engine SHALL select the Candidate_Point with the lower Max_Time.
4. WHERE Candidate_Points remain tied on Max_Time within the Equality_Threshold, THE Engine SHALL select the Candidate_Point with the lower Std_Dev.
5. WHERE Candidate_Points remain tied on Std_Dev within the Equality_Threshold, THE Engine SHALL select the Candidate_Point closest to the Geographic_Centroid.

### Requirement 3: Compute the Minimax Strategy

**User Story:** As a Participant, I want a meeting point that limits the worst individual trip, so that no single person is stuck with an unreasonably long journey.

#### Acceptance Criteria

1. WHEN a valid Meeting is accepted, THE Engine SHALL select as the Minimax_Strategy result the Candidate_Point that minimizes Max_Time (min max(t_i)).
2. WHERE two or more Candidate_Points have equal Max_Time within the Equality_Threshold, THE Engine SHALL select the Candidate_Point with the lower Sum_Time.
3. WHERE Candidate_Points remain tied on Sum_Time within the Equality_Threshold, THE Engine SHALL select the Candidate_Point with the lower Std_Dev.
4. WHERE Candidate_Points remain tied on Std_Dev within the Equality_Threshold, THE Engine SHALL select the Candidate_Point closest to the Geographic_Centroid.

### Requirement 4: Compute the Fairest Strategy

**User Story:** As a Participant, I want a meeting point that spreads travel time as evenly as possible without being wildly inefficient, so that the choice is fair to everyone while still being reasonable for the group.

#### Acceptance Criteria

1. WHEN a valid Meeting is accepted, THE Engine SHALL first compute the Fastest_Optimum T\* as the minimum Sum_Time across all Candidate_Points.
2. WHEN T\* is computed, THE Engine SHALL define the feasible set as all Candidate_Points whose Sum_Time satisfies Σ t_i(p) ≤ 1.15 · T\*, using the fixed Efficiency_Tolerance of 15%.
3. THE Engine SHALL treat the Efficiency_Tolerance as a fixed constant that is not user-configurable.
4. WHEN the feasible set is determined, THE Engine SHALL select as the Fairest_Strategy result the feasible Candidate_Point that minimizes Std_Dev.
5. WHERE two or more feasible Candidate_Points have equal Std_Dev within the Equality_Threshold, THE Engine SHALL select the Candidate_Point with the lower Sum_Time.
6. WHERE feasible Candidate_Points remain tied on Sum_Time within the Equality_Threshold, THE Engine SHALL select the Candidate_Point with the lower Max_Time.
7. WHERE feasible Candidate_Points remain tied on Max_Time within the Equality_Threshold, THE Engine SHALL select the Candidate_Point closest to the Geographic_Centroid.

### Requirement 5: Evaluate Candidate Points via Grid Search

**User Story:** As a Creator, I want the engine to search across many possible meeting points, so that the recommended points reflect real travel times rather than a simple midpoint.

#### Acceptance Criteria

1. WHEN a valid Meeting is accepted, THE Engine SHALL generate a set of N Candidate_Points via Grid_Search, using a grid or road intersections.
2. THE Engine SHALL compute Sum_Time, Max_Time, and Std_Dev for each Candidate_Point from the per-Participant Travel_Times.
3. THE Engine SHALL base all optimization on real Travel_Times and SHALL NOT select a meeting point solely by geographic center.
4. THE Engine SHALL isolate any city-specific values used during Grid_Search in Configuration so that the optimization logic remains city-agnostic.

### Requirement 6: Handle Routing Errors Transparently

**User Story:** As a Creator, I want to be told when a route cannot be computed and why, so that I can correct or remove the problematic location instead of getting silently wrong results.

#### Acceptance Criteria

1. IF Travel_Time cannot be computed for a Participant_Location, THEN THE Engine SHALL return an error status that identifies the specific Participant_Location and the specific reason.
2. WHEN a routing error is reported, THE System SHALL ask the Creator to correct or remove the problematic Participant_Location.
3. IF a route cannot be computed for a Participant_Location, THEN THE Engine SHALL NOT exclude that Participant_Location from the computation without notifying the Creator.
4. IF a route cannot be computed for a Participant_Location, THEN THE Engine SHALL NOT return Recommendations that silently omit the affected Participant.

### Requirement 7: Detect Outliers and Present the Trade-off

**User Story:** As a Creator, I want to see the effect of a participant who is exaggeratedly far away, so that I can decide with full transparency whether to include that person.

#### Acceptance Criteria

1. WHEN Travel_Times are computed for a Meeting, THE Engine SHALL evaluate each Participant against the configured Outlier detection rule and mark any Participant that exceeds the Outlier threshold as an Outlier.
2. WHERE a Meeting contains at least one Outlier, THE Engine SHALL compute the Fastest_Strategy, Minimax_Strategy, and Fairest_Strategy both including and excluding the Outlier.
3. WHERE a Meeting contains at least one Outlier, THE Engine SHALL present the difference between the including and excluding computations, reporting for each the selected meeting point and the group average Travel_Time.
4. THE System SHALL leave the decision to include or exclude an Outlier to the Creator.
5. THE Engine SHALL NOT exclude an Outlier from the results without presenting the excluded-versus-included comparison to the Creator.

### Requirement 8: Format Strategy Results

**User Story:** As a Creator, I want a clear result for each strategy, so that I can compare the fastest, minimax, and fairest options.

#### Acceptance Criteria

1. WHEN the Engine completes computation for a valid Meeting, THE Engine SHALL return exactly one Candidate_Point for each of the Fastest_Strategy, Minimax_Strategy, and Fairest_Strategy.
2. THE Engine SHALL include, for each strategy result, the per-Participant Travel_Time, Sum_Time, Max_Time, and Std_Dev.
3. WHEN the Engine returns strategy results, THE Engine SHALL return each result as an exact coordinate point.

### Requirement 9: Manage Meetings (CRUD without Authentication)

**User Story:** As a Creator, I want to create, view, edit, and delete meetings without signing in, so that I can coordinate quickly and revisit past meetings.

#### Acceptance Criteria

1. WHEN a Creator creates a Meeting, THE System SHALL persist the Meeting and generate a Meeting_URL that grants access to view and edit the Meeting.
2. THE System SHALL allow access to a Meeting through its Meeting_URL without any identity control.
3. WHEN a Creator requests a persisted Meeting, THE System SHALL return the stored Participant count, names, Participant_Locations, Transport_Mode, and Recommendations.
4. WHEN a Creator edits a Meeting's number of people, names, or Participant_Locations, THE System SHALL persist the changes.
5. WHEN a Creator deletes a Meeting, THE System SHALL remove the Meeting from persistent storage.
6. THE System SHALL retain persisted Meetings so that a Creator can access meeting history.
7. THE System SHALL resolve Participant_Locations from addresses entered through a search box with autocomplete backed by a geocoding service.
8. THE System SHALL NOT require selecting Participant_Locations by clicking on a map in the MVP.

### Requirement 10: User Experience and Internationalization

**User Story:** As a Creator, I want a comfortable desktop-first interface in my language with dark mode, so that the app is pleasant and understandable to use.

#### Acceptance Criteria

1. THE System SHALL present its interface in dark mode.
2. THE System SHALL provide a responsive layout optimized for desktop as the primary target.
3. THE System SHALL support Spanish and English content.
4. THE System SHALL provide a language selector that switches the interface between Spanish and English.
5. WHEN a Creator selects a language, THE System SHALL present subsequent interface content in the selected language.

### Requirement 11: API and Security Behavior

**User Story:** As a Creator, I want the service to behave securely and predictably, so that my requests are protected and the service stays available.

#### Acceptance Criteria

1. THE System SHALL expose its REST API under the version prefix /api/v1.
2. THE System SHALL enforce rate limiting on API requests and reject requests that exceed the configured rate limit.
3. THE System SHALL apply an explicit CORS policy that permits only the configured origins.
4. THE System SHALL keep third-party API keys in the backend and SHALL NOT expose them to the frontend.
5. THE System SHALL read city-specific values, service bounds, and provider settings from Configuration so that no Medellín-specific value is embedded in the optimization logic.

### Requirement 12: Out-of-Scope Behaviors (MVP Boundaries)

**User Story:** As a Creator, I want the MVP to stay focused on recommending a meeting point, so that the product is simple and clear about what it delivers.

#### Acceptance Criteria

1. THE System SHALL recommend only the meeting point and SHALL NOT suggest, book, or reserve a venue in the MVP.
2. THE System SHALL NOT provide formal sharing or invitation of individual Participants in the MVP.
3. THE System SHALL NOT provide filtering of results by establishment category in the MVP.
4. THE System SHALL NOT apply per-Participant travel constraints (for example a personal maximum travel time) in the MVP.

## Open Questions and Assumptions

These items are intentionally unresolved and MUST be calibrated with real data before being fixed as final values. They are recorded here so downstream design does not invent numbers.

- **Outlier numeric threshold (TBD)**: The rule for marking a Participant as an Outlier is expected to be of the form `t_i > k × group_median` (candidate k ≈ 2), or alternatively based on the 90th percentile of Travel_Times. The exact multiplier/percentile is pending calibration with real usage data. Requirement 7 references "the configured Outlier detection rule" so the threshold lives in Configuration.
- **Maximum search radius (TBD/open)**: The maximum geographic radius for Grid_Search and for constraining Candidate_Points is pending definition with real data and is treated as a Configuration value.
- **Grid density N**: The number of Candidate_Points generated by Grid_Search is a Configuration value to be tuned for accuracy versus cost; no fixed value is asserted here.

## Future (Post-MVP) Notes

These are recorded for context and are NOT requirements of the current MVP:

- **Incremental improvement 1**: Return a sector/zone instead of a single exact point per strategy.
- **Incremental improvement 2**: List real establishments within the recommended zone.
