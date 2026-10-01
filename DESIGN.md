---
name: MeetHalfway
description: A civic route-planning workspace for fair group meeting decisions.
colors:
  primary: "#1f6b5d"
  primary-deep: "#155447"
  primary-soft: "#dcece4"
  navy-ink: "#12243a"
  paper: "#f8f7f1"
  raised-surface: "#ffffff"
  field: "#e9ece6"
  rule: "#cad2cb"
  muted-ink: "#526476"
  exception: "#b64032"
  warning: "#a9681e"
typography:
  body:
    fontFamily: "Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.5
  title:
    fontFamily: "Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif"
    fontSize: "1.15rem"
    fontWeight: 760
    lineHeight: 1.2
    letterSpacing: "-0.025em"
  label:
    fontFamily: "Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif"
    fontSize: "0.68rem"
    fontWeight: 750
    letterSpacing: "0.1em"
rounded:
  xs: "5px"
  sm: "8px"
  md: "14px"
  lg: "18px"
spacing:
  1: "0.25rem"
  2: "0.5rem"
  3: "0.75rem"
  4: "1rem"
  5: "1.5rem"
  6: "2rem"
  7: "3rem"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.paper}"
    rounded: "{rounded.sm}"
    padding: "0.5rem 0.75rem"
    height: "48px"
  button-primary-hover:
    backgroundColor: "{colors.primary-deep}"
    textColor: "{colors.paper}"
    rounded: "{rounded.sm}"
  input-ledger:
    backgroundColor: "transparent"
    textColor: "{colors.navy-ink}"
    rounded: "0"
    padding: "0.5rem 0"
  nav-service:
    backgroundColor: "{colors.navy-ink}"
    textColor: "{colors.paper}"
    height: "64px"
---

# Design System: MeetHalfway

## Overview

**Creative North Star: "The Civic Route Ledger"**

MeetHalfway is an operational planning surface, not a dark map dashboard. A mineral-paper decision rail and a midnight service header make group inputs, journey evidence, and exceptions feel accountable. The live map remains the working field; the rail records the decision around it.

The system is precise without becoming technical theatre. Fine rules, tabular values, short labels, and one controlled emerald active state create the visual logic. Ambient elevation appears only where a map panel must float above the field.

**Key Characteristics:**

- Paper-like planning surfaces paired with navy service chrome.
- Ruled ledgers in place of generic card stacks.
- Emerald reserved for action, selection, and the Fairest recommendation.
- Responsive panels preserve separate working bands over the map.

## Colors

The palette is restrained: civic navy provides authority, paper supports reading, and emerald is valuable precisely because it is scarce.

### Primary

- **Active Route Emerald:** the shared action, selected-person state, and Fairest marker.
- **Deep Service Emerald:** hover and pressed emphasis for the primary action.
- **Route Wash:** selected-row and selected-comparison grounding.

### Secondary

- **Midnight Service Ink:** header, key type, origin markers, and strong controls.
- **Exception Coral:** routing failures and corrective actions only.
- **Signal Ochre:** warnings that should be noticed without becoming errors.

### Neutral

- **Mineral Paper:** decision-rail ground and overlay surface.
- **Raised Paper:** popup, result, and input-adjacent surfaces.
- **Planning Field:** application backdrop behind the route workspace.
- **Ledger Rule:** dividers and low-priority boundaries.
- **Muted Ink:** secondary explanation and coordinate context.

**The One Active Route Rule.** Emerald signals an actionable or selected state; it is never decorative fill.

## Typography

**Body Font:** Inter with the system sans-serif fallback stack.

**Character:** Compact, humanist, and deliberately quiet. Weight and spacing create hierarchy rather than a separate display typeface.

### Hierarchy

- **Title:** weight 760 with close tracking; section names and the planning rail heading.
- **Body:** regular, 1rem / 1.5; explanations and form copy.
- **Supporting text:** approximately .82–.88rem; contextual travel and map information.
- **Label:** .68rem, weight 750, uppercase with wide tracking; transport and data labels only.

**The Ledger Type Rule.** Measurements use tabular numerals and remain right-aligned against their labels when compared.

## Layout

Desktop is a permanent two-part workspace: a 340–390px decision rail on the left and the live map filling the remaining width. The rail uses generous outer spacing and close internal grouping separated by rules.

Below 700px, the rail becomes a bottom drawer. Results occupy a bounded band above its collapsed 64px edge, while participant detail occupies a separately bounded top band and scrolls internally. Strategy comparisons collapse from three columns to a ruled vertical ledger below 900px.

## Elevation & Depth

The system is flat by default. `0 12px 28px rgba(18, 36, 58, 0.12)` defines local map control elevation; `0 18px 42px rgba(18, 36, 58, 0.18)` defines floating result and detail panels. Neither is paired with a border.

**The Field Elevation Rule.** A shadow means a surface is temporarily above the map; ordinary records stay flat and use rules for separation.

## Shapes

Controls use gently curved corners: 5px for compact list and popup edges, 8px for buttons and inputs, 14px for floating panels, and 18px only for the mobile drawer. Lists, records, and inputs prefer square rule-led geometry over boxed cards. Avatars and map origins may be circular because they represent people and locations.

## Components

### Buttons

- **Primary:** an emerald 48px planning action with paper text, 8px corners, and a small upward hover movement.
- **Secondary / text:** transparent with emerald or coral text; used for adding and removing participant records.
- **Focus:** the shared soft emerald focus ring; keyboard focus is never a browser-default blue outline.

### Inputs / Fields

- **Style:** transparent fields with a single bottom rule, not outlined boxes.
- **Focus:** the shared focus ring and emerald caret.
- **Suggestions:** raised paper with a stronger outline, retaining the same ledger rhythm.

### Navigation

- **Service header:** a 64px midnight bar with a circular route mark, compact product title, and fine active underline.
- **Mobile:** title remains while secondary navigation recedes, preserving language selection.

### Participant Manifest

Each person is a ruled row with initials, name, and selected state. Selection changes the row to Route Wash and the related map origin to the emerald active state.

### Strategy Ledger

Fastest, Minimax, and Fairest are columns of one comparison ledger rather than three floating cards. Fairest alone receives the Route Wash ground; travel-time values use tabular numerals.

### Map Field

Leaflet controls and popups use Raised Paper, softened corners, and ambient elevation. Origin markers use navy outlines; strategy markers stay muted except Fairest, which receives emerald.

## Do's and Don'ts

### Do:

- **Do** use rules and alignment to show hierarchy before introducing a container.
- **Do** reserve emerald for the meeting calculation, a selected participant, and the Fairest outcome.
- **Do** keep map overlays bounded and independently scrollable on mobile.
- **Do** use tabular numerals wherever journey times or coordinates are compared.

### Don't:

- **Don't** return to a generic dark sidebar-and-map shell.
- **Don't** create nested, same-weight cards around every form or result group.
- **Don't** use blue or purple as a competing active route color.
- **Don't** use gradients, decorative glass, hard shadows, or decorative icon glyphs.
