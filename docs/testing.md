# Testing and verification

## Backend

Use Java 25 (the project compiler target) and run from `backend/`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd clean verify
```

Tests cover endpoint/method contracts, DTO mapping, CRUD and persistence
round-trips, provider failures, architecture boundaries, and algorithmic
invariants with jqwik. Property tests are deterministic per run seed and should
be retained when changing the optimizer.

## Frontend

Run from `frontend/`:

```powershell
npm.cmd run typecheck
npm.cmd test
npm.cmd run build
npm.cmd run test:e2e
```

Vitest covers API semantics, meeting flow state, component behavior, Leaflet
marker data, and stale autocomplete responses. Playwright runs Chromium against
the Vite development server; its tests mock API responses so they do not depend
on PostgreSQL, OSRM, or Nominatim. Install the browser once with
`npx playwright install chromium`.

## Stack smoke checks

The browser smoke tests do not replace deployment verification. For a full local
stack, run `docker compose config`, `docker compose build`, then
`docker compose up --wait`; verify the health endpoint, meeting creation,
recommendations, provider failure behavior, and database persistence through
Nginx. Keep backend CORS as the single CORS policy owner.

GitHub Actions runs the Java 25 backend verification and the frontend typecheck,
unit tests, production build, and Chromium smoke tests on pushes and pull
requests.
