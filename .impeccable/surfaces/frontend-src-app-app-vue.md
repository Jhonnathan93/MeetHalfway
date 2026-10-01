---
version: 1
slug: "frontend-src-app-app-vue"
primary_target: "frontend/src/app/App.vue"
related_targets: ["frontend/src/features/meeting/MeetingView.vue","frontend/src/features/meeting/components/MeetingForm.vue","frontend/src/features/meeting/components/StrategyComparisonView.vue","frontend/src/features/meeting/map/MapView.vue"]
---

## Scope

The meeting workspace is the complete web-app surface. Mode: Operate.

## Audience and job

Groups coordinating an in-person meet-up need to enter origins, choose travel mode, understand the consequences of each fairness strategy, and agree on a place.

## Action, proof, and constraints

The user adds real addresses, calculates recommendations, reads actual travel-time evidence, inspects people on the map, and can resolve routing or outlier states. Preserve all current Vue, API, map, language, keyboard, and responsive behavior.

## Chosen direction and memorable moment

Civic Route Ledger. The map is the active planning field; the decision rail is a calm, transport-grade record of the group’s inputs and recommendations. The memorable moment is a selected person becoming the active state across the rail and map through one emerald service color.

## Unresolved decisions

None.

## Direction contract

THESIS: A shared meeting plan should read like a trustworthy civic route desk, refusing the generic dark sidebar-plus-map shell.

OWN-WORLD: Mineral-white planning surfaces, midnight navy ink, emerald as the only live route color, and restrained coral for exceptions; compact humanist type, tabular numerals, fine rules, square-to-soft corners, and map notation as functional evidence.

STORY: A group member understands that fairness comes from actual journey time, enters each origin, runs the calculation, then compares the three defensible meeting points without losing context.

FIRST VIEWPORT: A slim navy service header sits over a fixed, paper-white decision rail on the left and a wide live map on the right. The rail contains the plan title, participant manifest, travel-mode switch, address sequence, and the full-width calculate action. The map’s quiet state explains the next task; returned evidence rises from the map edge as a route ledger, not a floating card.

FORM: Civic Route Ledger, third of seven grounded directions; seed key e4fe7770. Signature interaction: selecting a person applies a reduced-motion-safe emerald active state to the associated ledger row and map marker.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
