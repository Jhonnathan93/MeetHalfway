# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

People coordinating a shared in-person meeting: friends, colleagues, and small groups who need to compare travel from several starting points and agree on a practical location.

## Product Purpose

MeetHalfway finds a fair place for a group to meet using actual travel time rather than a simple geographic midpoint. Success is a group being able to enter locations, understand the alternatives, and confidently choose a meeting point.

## Positioning

The product evaluates real route travel and presents Fastest, Minimax, and Fairest recommendations, making the trade-offs behind a shared meeting location visible rather than treating the map center as the answer.

## Operating Context

Users add participant locations, choose a shared transport mode, review a map with recommendation markers and participant detail, compare strategies, and may resolve an outlier trade-off or routing failure.

## Capabilities and Constraints

- Preserve all existing frontend behavior, API integrations, map interactions, recommendation strategies, validation, error handling, language selection, and responsive use.
- The current application is a Vue 3 + TypeScript single-page web app with a Spring Boot API.
- The redesign is code-first and must not rely on visual mockups as implementation authority.

## Brand Commitments

MeetHalfway communicates calm, transparent fairness in shared travel decisions. Its existing product name and strategy terminology remain intact.

## Evidence on Hand

The repository contains the live meeting workflow, translated interface copy, strategy descriptions, map implementation, and automated tests. There are no supplied brand assets, testimonials, or external proof claims to invent.

## Product Principles

1. Make the fairest choice understandable at a glance.
2. Show travel trade-offs instead of hiding them behind a single answer.
3. Keep group coordination approachable and low-friction.
4. Let the map and the recommendation evidence work together.

## Accessibility & Inclusion

The interface must remain keyboard accessible, readable at responsive sizes, and respectful of reduced-motion preferences.
