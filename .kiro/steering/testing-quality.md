---
inclusion: fileMatch
fileMatchPattern: ["backend/**/src/test/**/*", "backend/**/*Test.java", "frontend/**/*.test.ts", "frontend/**/*.spec.ts", "frontend/e2e/**/*", "**/playwright.config.*", "**/vitest.config.*"]
---

# Testing and quality standards

- Maintain at least 80% MVP coverage through a balanced unit, integration, and small critical-path E2E suite.
- Use unit tests for domain calculations and use cases; integration tests for adapters, HTTP/persistence boundaries, and provider contracts; E2E tests only for critical user journeys.
- Property-based testing is mandatory. For optimization, generate valid candidate/participant travel-time inputs and assert invariants: selected candidates are evaluated and feasible, ties resolve deterministically, Fairest obeys its total-time constraint, and no routing failure is silently discarded.
- Test all three strategies independently, including epsilon-boundary ties, an empty feasible Fairest set, outlier comparisons, 2- and 10-person meetings, both travel modes, and unroutable locations.
- Frontend tests use Vitest; E2E tests use Playwright. Use ESLint, Prettier, and strict TypeScript checks.
- Backend uses the Java ecosystem equivalents for linting, formatting, static analysis, unit/integration testing, and PBT.
- CI on every PR runs: lint, type/static check, unit tests, property tests, and build. Run E2E on `main`. Dependency scanning is required; SonarQube/SonarCloud and mutation testing are explicitly out of scope.
