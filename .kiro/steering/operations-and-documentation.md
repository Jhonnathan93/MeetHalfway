---
inclusion: auto
name: operations-and-documentation
description: Docker, Docker Compose, Nginx, GCP deployment, CI/CD, GitHub Actions, rollback, repository documentation, ADRs, branches, pull requests, or commits.
---

# Delivery, documentation, and Git standards

- Local development must work through Docker Compose with frontend, backend, PostgreSQL, and Nginx as appropriate. Deployment targets one GCP instance using Docker Compose and Nginx.
- Keep deployment configuration free of secrets. Use ignored environment files or runtime secret configuration, and document required variable names in a safe example file.
- GitHub Actions runs lint, type/static check, unit tests, property tests, and build for every PR. E2E runs on `main`. Deploy automatically after merge to `main` and retain a rollback path for post-deploy failures.
- Use branches `feature/*`, `fix/*`, and `docs/*` from `main`; do not create a `development` branch. Prefer PRs even for solo work. Rebase rather than squash merge.
- Use English Conventional Commits with scopes, for example `feat(routing): ...`, `fix(map): ...`, `test(optimization): ...`, `docs(...): ...`, `refactor(...): ...`, `chore(...): ...`, or `ci(...): ...`.
- Keep the required documentation current: `README.md`, `docs/architecture.md`, `docs/algorithms.md`, `docs/testing.md`, `docs/decisions/ADR-001-routing-provider.md`, and `docs/decisions/ADR-002-optimization-strategies.md`.
- Write ADRs only for significant decisions, such as routing-provider selection or strategy design. Record why the decision was made, its consequences, and any calibration that resolves currently open product thresholds.
- Use Kiro Specs for work tracking rather than GitHub Issues.
