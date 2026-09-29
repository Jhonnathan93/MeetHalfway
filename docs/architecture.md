# Architecture

MeetHalfway keeps the web contract at the edge and the recommendation algorithm
independent of Spring, JPA, and HTTP.

```text
HTTP controllers
  ├─ MeetingService (meeting lifecycle)
  └─ RecommendationService (load → compute → persist success)
       └─ RecommendationEngine (pure domain logic)
            ├─ MeetingRepository → JPA adapter → PostgreSQL
            └─ RoutingProvider → OSRM adapter

LocationController → GeocodingProvider → Nominatim adapter
```

Controllers translate requests and outcomes; services coordinate use cases; the
domain engine owns validation, grid candidates, travel-time metrics, strategy
selection, deterministic tie-breaking, and outlier comparison. `MeetingRepository`,
`RoutingProvider`, and `GeocodingProvider` remain interfaces because each is a
real boundary to persistence or a remote system. Internal algorithm collaborators
are concrete classes because the project has one implementation of each.

Persistence uses separate JPA entities and a mapper. A meeting only stores an
optional successful recommendation; transient routing failures are returned to
the caller and never mapped to database rows. Flyway owns schema changes.

The frontend is organized by the single workflow it implements:

```text
frontend/src/
├─ app/                       # Vue entry point and application shell
├─ features/meeting/          # flow, API, types, UI, and Leaflet map
└─ shared/                    # HTTP, i18n, styles, formatting, language control
```

Only `shared/api/http.ts` performs HTTP transport. The meeting API module exposes
typed operations; Vue components do not know backend URLs. There is no router or
global store because the current frontend has one view and one flow.

The public `/api/v1` endpoints and JSON fields are compatibility boundaries.
`candidateId`, `recommended`, and `warnings` remain in the response contract for
this version even where the UI can derive the same display information.
