---
inclusion: fileMatch
fileMatchPattern: ["frontend/**/*.ts", "frontend/**/*.vue", "frontend/**/*.css", "frontend/**/*.scss", "frontend/**/*.json"]
---

# Frontend standards

- Use Vue and TypeScript with `strict: true`. Prefer typed props, emits, composables, API contracts, and state over `any` or implicit assumptions.
- Use PascalCase filenames for TypeScript and Vue component classes/types; use camelCase for variables and functions; use UPPER_SNAKE_CASE for constants.
- Build a desktop-first, responsive interface with dark mode and Spanish/English localization. Do not hard-code user-visible copy in a single language.
- The create/edit meeting flow collects participant name and location through address autocomplete. Do not implement map-click input.
- A meeting has 2--10 participants and exactly one shared mode (`DRIVING` or `WALKING`). Validate these constraints in the UI for fast feedback, while retaining backend validation as the authority.
- Display all three results clearly: Fastest, Minimax, and Fairest. Explain their travel-time trade-offs without implying that a geographic center is fair.
- When the backend reports an unroutable participant location, show its specific reason and a clear correction/removal action. Never hide that participant or fabricate a result.
- When an outlier is detected, present the with/without-outlier comparison transparently. The user chooses whether to include the person; do not exclude anyone automatically.
- Call only `/api/v1` backend endpoints. The browser must never call geocoding, routing, or places providers with secret credentials.
- Use ESLint, Prettier, Vitest, and Playwright. Cover critical end-to-end flows sparingly: create a meeting, edit a meeting, view results, and handle routing errors.
