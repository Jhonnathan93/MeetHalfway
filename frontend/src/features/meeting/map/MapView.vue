<script setup lang="ts">
/**
 * Leaflet map visualization of the recommendation (R11). View-only: it renders
 * the candidate points, recommended point, and participant origins the backend
 * already computed — it never recomputes points, routing, or scores (R10.1).
 *
 * The map derives everything from props (the request phase, strategy results,
 * participant origins, and backend warnings). Depending on the phase it shows a
 * loading state (R11.8), an error state that clears stale markers (R11.9), an
 * empty state when there are no candidates (R11.6), or the interactive map with
 * candidate markers grouped by strategy (R11.1), participant
 * origins (R11.5), auto-fit bounds (R11.3), and popups (R11.4). Out-of-range
 * coordinates are excluded and surfaced through a non-blocking notice (R11.7).
 *
 * All user-visible copy flows through vue-i18n (ES/EN); nothing is hard-coded.
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { STRATEGY_KEYS, type Coordinate, type CoordinateWarning, type StrategyKey } from '@/features/meeting/types'
import type { Participant, StrategyResults } from '@/features/meeting/types'
import { formatMetric, formatMinutes } from '@/shared/formatting/formatMinutes'
import {
  isValidCoordinate,
  useMeetingMap,
  type CandidateMarker,
  type OriginMarker,
} from './useMeetingMap'

/** The request phase driving which state the map presents. */
export type MapPhase = 'loading' | 'error' | 'success'

const props = defineProps<{
  /** Current request phase (from App.vue's submitting/errorMessage refs). */
  phase: MapPhase
  /** Strategy results to render; null when no recommendation is present. */
  results: StrategyResults | null
  /** Participant origins (from MeetingResponse.participants[].location). */
  participants: Participant[]
  /** Backend coordinate warnings; may be empty. */
  warnings: CoordinateWarning[]
  /** Optional human-readable error text for the error state. */
  errorMessage?: string
}>()

const { t } = useI18n()

const mapContainer = ref<HTMLElement | null>(null)
const controller = useMeetingMap()

const minutesUnit = computed(() => t('results.minutes'))

/** Localized popup HTML for a candidate: candidateId + Σ / max / σ (R11.4). */
function candidatePopupHtml(strategy: StrategyKey, sumTime: number, maxTime: number, stdDev: number): string {
  const rows = [
    `<strong>${escapeHtml(t(`results.${strategy}`))}</strong>`,
    `${escapeHtml(t('results.sumTime'))}: ${escapeHtml(formatMetric(sumTime))} ${escapeHtml(minutesUnit.value)}`,
    `${escapeHtml(t('results.maxTime'))}: ${escapeHtml(formatMinutes(maxTime, minutesUnit.value))}`,
    `${escapeHtml(t('results.stdDev'))}: ${escapeHtml(formatMetric(stdDev))}`,
  ]
  return `<div class="map-popup">${rows.join('<br />')}</div>`
}

/** Minimal HTML escaping so derived text never injects markup into popups. */
function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** Candidate markers derived from the three strategy results (no recompute). */
const candidateMarkers = computed<CandidateMarker[]>(() => {
  if (!props.results) {
    return []
  }
  return STRATEGY_KEYS.map((key) => {
    const result = props.results![key]
    return {
      strategy: key,
      point: result.point,
      popupHtml: candidatePopupHtml(key, result.sumTime, result.maxTime, result.stdDev),
    }
  })
})

/** Participant-origin markers (2–10) derived from the meeting participants. */
const originMarkers = computed<OriginMarker[]>(() =>
  props.participants.map((participant) => ({
    participantId: participant.id,
    label: participant.name.length > 0 ? participant.name : participant.id,
    point: participant.location,
  })),
)

/** True once the phase is success and there is at least one candidate (R11.6). */
const hasCandidates = computed(() => props.phase === 'success' && candidateMarkers.value.length > 0)

/**
 * Points excluded because their coordinates are out of range, surfaced as a
 * non-blocking notice (R11.7). Backend `warnings` are mirrored, and any locally
 * invalid candidate/origin is added so the notice never under-reports.
 */
interface ExcludedPoint {
  reference: string
  label: string
  point: Coordinate
}

const excludedPoints = computed<ExcludedPoint[]>(() => {
  const seen = new Set<string>()
  const excluded: ExcludedPoint[] = []

  const add = (reference: string, label: string, point: Coordinate): void => {
    if (seen.has(reference)) {
      return
    }
    seen.add(reference)
    excluded.push({ reference, label, point })
  }

  if (props.phase === 'success') {
    for (const warning of props.warnings) {
      const label =
        warning.kind === 'CANDIDATE'
          ? t('map.excludedCandidate', { id: warning.reference })
          : t('map.excludedOrigin', { id: warning.reference })
      add(warning.reference, label, { lat: warning.lat, lng: warning.lng })
    }
    for (const candidate of candidateMarkers.value) {
      if (!isValidCoordinate(candidate.point)) {
        add(candidate.strategy, t('map.excludedCandidate', { id: candidate.strategy }), candidate.point)
      }
    }
    for (const origin of originMarkers.value) {
      if (!isValidCoordinate(origin.point)) {
        add(origin.participantId, t('map.excludedOrigin', { id: origin.label }), origin.point)
      }
    }
  }
  return excluded
})

function draw(): void {
  if (props.phase !== 'success') {
    // Loading or error: never show markers from a previous response (R11.9).
    controller.clear()
    return
  }
  controller.render({ candidates: candidateMarkers.value, origins: originMarkers.value })
}

// Mount the Leaflet map when the container becomes available (success phase),
// then keep markers in sync with the props.
watch(
  [mapContainer, () => props.phase, candidateMarkers, originMarkers],
  () => {
    if (mapContainer.value && props.phase === 'success') {
      controller.mount(mapContainer.value)
    }
    draw()
  },
  { flush: 'post' },
)

onBeforeUnmount(() => {
  controller.destroy()
})
</script>

<template>
  <section class="map-view" aria-label="map">
    <h2 class="map-view__heading">{{ t('map.heading') }}</h2>

    <p v-if="phase === 'loading'" class="map-view__state" role="status">
      {{ t('map.loading') }}
    </p>

    <p v-else-if="phase === 'error'" class="map-view__state map-view__state--error" role="alert">
      {{ errorMessage && errorMessage.length > 0 ? errorMessage : t('map.error') }}
    </p>

    <p v-else-if="!hasCandidates" class="map-view__state" role="status">
      {{ t('map.empty') }}
    </p>

    <template v-else>
      <div ref="mapContainer" class="map-view__canvas" data-testid="map-canvas" />

      <ul
        v-if="excludedPoints.length > 0"
        class="map-view__notice"
        role="note"
        aria-label="excluded-points"
      >
        <li class="map-view__notice-heading">{{ t('map.excludedHeading') }}</li>
        <li v-for="excluded in excludedPoints" :key="excluded.reference">
          {{ excluded.label }}
          <span class="map-view__notice-coord">
            ({{ excluded.point.lat }}, {{ excluded.point.lng }})
          </span>
        </li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.map-view {
  margin-top: var(--space-4);
}

.map-view__heading {
  margin: 0 0 var(--space-3);
  font-size: 1.25rem;
}

.map-view__state {
  color: var(--color-text-muted);
  margin: 0;
}

.map-view__state--error {
  color: var(--color-danger);
}

.map-view__canvas {
  width: 100%;
  height: 420px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  /* Leaflet needs an explicit background so tiles loading is not jarring. */
  background-color: var(--color-surface-raised);
}

.map-view__notice {
  list-style: none;
  margin: var(--space-3) 0 0;
  padding: var(--space-3);
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-md);
  background-color: var(--color-surface-raised);
  display: grid;
  gap: var(--space-1);
}

.map-view__notice-heading {
  color: var(--color-danger);
  font-weight: 600;
}

.map-view__notice-coord {
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
  font-size: 0.8125rem;
}

/* Marker visual treatments: recommendations use one color per strategy. */
:global(.map-marker) {
  border-radius: 50%;
  box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.35);
}

:global(.map-marker--fastest) {
  background-color: var(--color-success);
  border: 3px solid #ffffff;
}

:global(.map-marker--minimax) {
  background-color: var(--color-primary);
  border: 2px solid #ffffff;
}

:global(.map-marker--fairest) {
  background-color: #b56de8;
  border: 2px solid #ffffff;
}

:global(.map-marker--origin) {
  background-color: var(--color-danger);
  border: 2px solid #ffffff;
}
</style>
