# MeetHalfway

Find the fairest place to meet based on **actual travel time**, not the
geographic center of the group. Given participant locations and a shared
transport mode, the engine returns three recommended meeting points —
**Fastest**, **Minimax**, and **Fairest**.

## Repository layout

```
meethalfway/            (this repository root)
├── frontend/           # Vue 3 + TypeScript (strict) SPA
├── backend/            # Spring Boot; Clean Architecture layers
├── nginx/              # Reverse proxy: terminates HTTP, routes /api/v1, CORS
├── docs/
│   ├── architecture.md
│   ├── algorithms.md
│   ├── testing.md
│   └── decisions/
│       ├── ADR-001-routing-provider.md
│       └── ADR-002-optimization-strategies.md
├── .kiro/
└── docker-compose.yml
```

## Running locally

```bash
docker compose up --build
```

Nginx listens on port 80 and proxies `/api/v1` to the backend; everything else
is served by the frontend. Configure allowed CORS origins, database
credentials, and backend-only provider API keys via a `.env` file (see
`docker-compose.yml` for the variables).
