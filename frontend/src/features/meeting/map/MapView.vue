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

const props = withDefaults(defineProps<{
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
  selectedParticipantId?: string | null
}>(), {
  selectedParticipantId: null,
})

const emit = defineEmits<{ selectParticipant: [participantId: string] }>()

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
    initials: (participant.name.trim().slice(0, 2) || participant.id.slice(0, 2)).toUpperCase(),
    point: participant.location,
    selected: participant.id === props.selectedParticipantId,
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
  controller.render({
    candidates: candidateMarkers.value,
    origins: originMarkers.value,
    onOriginSelect: (participantId) => emit('selectParticipant', participantId),
  })
}

// Mount the Leaflet map when the container becomes available (success phase),
// then keep markers in sync with the props.
watch(
  [mapContainer, () => props.phase, candidateMarkers, originMarkers, () => props.selectedParticipantId],
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
  <section class="map-view" :aria-label="t('map.canvasLabel')">
    <div ref="mapContainer" class="map-view__canvas" data-testid="map-canvas" />
    <div v-if="phase === 'loading' || phase === 'error' || !hasCandidates" class="map-view__state" :class="{ 'map-view__state--error': phase === 'error' }" :role="phase === 'error' ? 'alert' : 'status'">
      <span class="map-view__state-icon" aria-hidden="true">{{ phase === 'error' ? '!' : phase === 'loading' ? '…' : '◎' }}</span>
      <div>
        <h2>{{ phase === 'loading' ? t('map.loadingTitle') : phase === 'error' ? t('map.errorTitle') : t('map.noPointTitle') }}</h2>
        <p>{{ phase === 'loading' ? t('map.loading') : phase === 'error' ? (errorMessage && errorMessage.length > 0 ? errorMessage : t('map.error')) : t('map.noPointDescription') }}</p>
      </div>
    </div>

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
  </section>
</template>

<style scoped>
.map-view {
  position: absolute;
  inset: 0;
}

.map-view__state {
  position: absolute;
  z-index: 500;
  top: 50%;
  left: 50%;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: min(390px, calc(100% - 48px));
  padding: var(--space-4);
  color: var(--color-text-muted);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-map-overlay);
  box-shadow: var(--shadow-panel);
  transform: translate(-50%, -50%);
}

.map-view__state h2 { margin: 0 0 3px; color: var(--color-text); font-family: var(--font-display); font-size: 1.45rem; font-weight: 400; letter-spacing: .035em; }
.map-view__state p { margin: 0; font-size: .82rem; line-height: 1.5; }
.map-view__state-icon { display: grid; flex: 0 0 auto; place-items: center; width: 32px; height: 32px; border-radius: 50%; color: var(--color-primary-hover); background: var(--color-primary-soft); font-size: 1.1rem; font-weight: 800; }
.map-view__state--error .map-view__state-icon { color: var(--color-danger); background: rgba(255, 104, 121, .13); }

.map-view__state--error h2 {
  color: var(--color-danger);
}

.map-view__canvas {
  width: 100%;
  height: 100%;
  background-color: var(--color-surface-raised);
}

.map-view__notice {
  list-style: none;
  position: absolute;
  z-index: 500;
  top: var(--space-4);
  left: var(--space-4);
  max-width: min(420px, calc(100% - 32px));
  margin: 0;
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
  display: grid;
  place-items: center;
  border-radius: 50%;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.35);
  color: #fff;
  font-family: var(--font-sans);
  font-size: .62rem;
  font-weight: 800;
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
  background-color: #eef2fa;
  border: 3px solid #263246;
  color: #182232;
}

:global(.map-marker--origin-selected) {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 5px rgba(91, 140, 255, .3), 0 4px 12px rgba(0, 0, 0, .45);
}
</style>
