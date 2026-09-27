---
inclusion: fileMatch
fileMatchPattern: ["backend/**/*.java", "backend/**/*.xml", "backend/**/*.yml", "backend/**/*.yaml", "backend/**/*.properties"]
---

# Backend and API standards

- Use Spring Boot, PostgreSQL, Java naming conventions, and explicit dependency injection.
- Maintain Clean Architecture boundaries: domain and use cases must not depend on Spring MVC, JPA, PostgreSQL, or a concrete third-party provider.
- Define explicit provider ports such as `GeocodingProvider`, `RoutingProvider`, and `PlacesProvider`; concrete API clients live in adapters/infrastructure and are replaceable without core changes.
- Java classes and filenames use PascalCase; methods and variables use camelCase; constants use UPPER_SNAKE_CASE. Avoid ambiguous utility objects: utility methods are explicit class methods, for example `NameUtils.normalize()`.
- Expose REST endpoints only under `/api/v1`. Use kebab-case URL segments and camelCase JSON properties. Validate request bodies at the boundary and return consistent, actionable error responses.
- Enforce meeting rules server-side: 2--10 participants, shared supported travel mode, valid/sanitized participant data and coordinates.
- Persist meetings and support unauthenticated CRUD through the meeting URL/identifier. Do not add authentication or invitations in the MVP.
- If an external routing result is unavailable, preserve the affected participant and return a specific error explaining why they must correct or remove the location. Do not silently omit them from calculation.
- Configure CORS deliberately, rate-limit exposed endpoints, and load API keys only from backend runtime configuration. Never return, log, or serialize secrets.
- Do not introduce Redis/caching in the MVP. Keep city-specific configuration outside algorithms.
- Use the Java equivalents of linting, formatting, static typing/analysis, unit testing, integration testing, and property-based testing. Target at least 80% MVP coverage.
