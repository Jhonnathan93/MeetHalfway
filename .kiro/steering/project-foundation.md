---
inclusion: always
---

# MeetHalfway: product and architecture foundation

MeetHalfway helps a group find the fairest meeting point based on real travel time, not geographic distance. The product promise is: **"Find the fairest place for everyone to meet based on actual travel time."**

## MVP boundaries

- The initial market is Medellín and the Valle de Aburrá metropolitan area. Keep city-specific limits and data in configuration; optimization logic must remain city-agnostic.
- A meeting has 2 to 10 participants. There is no authentication in the MVP.
- The meeting creator enters and edits all participant names and locations. Meeting URLs allow access and editing without identity control.
- Support one shared travel mode per meeting: `DRIVING` and `WALKING`. Mixed modes are out of scope.
- Address entry uses geocoding autocomplete, never map-click selection.
- Persist meeting history.
- Return one representative coordinate per strategy in the MVP. Do not add place recommendations, reservations, venue-category filters, individual travel-time constraints, participant invitations, or a native mobile app.

## Product experience

- Build desktop-first but responsive UI, with dark mode.
- All user-facing text, code, documentation, tests, branches, and commits are in English.
- Provide Spanish and English with an explicit language selector.
- The API is REST and starts at `/api/v1`; use kebab-case paths and camelCase JSON fields.

## Architecture rules

- Use a monorepo layout: `frontend/`, `backend/`, `docs/`, `.kiro/`, `nginx/`, and root `docker-compose.yml`.
- The frontend is Vue with TypeScript in strict mode. The backend is Spring Boot with PostgreSQL.
- Apply Clean Architecture and SOLID. Keep domain/application code independent of HTTP, persistence, and external-provider details.
- Model external integrations behind explicit interfaces/adapters. At minimum use `GeocodingProvider`, `PlacesProvider`, and `RoutingProvider`; inject implementations explicitly.
- The MVP has no Redis or other cache.
- The project must run fully locally through Docker Compose and be deployable to a single GCP instance through Docker Compose and Nginx.

## Non-negotiable safety and privacy

- Validate and sanitize all input, including coordinate ranges.
- Configure CORS explicitly and apply rate limiting.
- Keep external API keys on the backend only. Never put secrets in frontend bundles, tracked files, logs, tests, or documentation. Ignore `.env` in Git.
- Use only free/open-source technologies or free-tier services with explicit rate limits.
- If routing cannot be calculated for a participant, report the specific reason and require the user to correct or remove that location. Never silently drop it.

## Open decisions

Do not hard-code or expose as user controls the final values for these items until they are calibrated with real team data:

- Fairest efficiency tolerance (15% is a working example only).
- Outlier threshold (2x group median and percentile 90 are working examples only).
- Maximum optimal-zone search radius.

Place the eventual values in configuration and document the decision in an ADR or relevant algorithm documentation.
