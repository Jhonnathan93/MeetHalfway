<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Participant, StrategyResults, TransportMode } from '@/features/meeting/types'
import { formatMinutes } from '@/shared/formatting/formatMinutes'

const props = defineProps<{
  participant: Participant
  results: StrategyResults | null
  transportMode: TransportMode
}>()

const emit = defineEmits<{ close: [] }>()
const { t } = useI18n()
const initials = computed(() => props.participant.name.trim().slice(0, 2).toUpperCase() || props.participant.id.slice(0, 2).toUpperCase())
const travelTime = computed(() => props.results?.fastest.perParticipant[props.participant.id])
const meetingPoint = computed(() => props.results?.fastest.point ?? null)
const transportLabel = computed(() => t(props.transportMode === 'driving' ? 'participants.car' : 'participants.foot'))
const coordinate = (point: { lat: number; lng: number }) => t('participants.coordinates', { lat: point.lat.toFixed(5), lng: point.lng.toFixed(5) })
</script>

<template>
  <aside class="participant-details" :aria-label="t('participants.detail')">
    <header class="participant-details__header">
      <span class="participant-details__avatar" aria-hidden="true">{{ initials }}</span>
      <div>
        <h2>{{ participant.name || participant.id }}</h2>
        <p>{{ coordinate(participant.location) }}</p>
      </div>
      <button type="button" class="participant-details__close" :aria-label="t('participants.close')" @click="emit('close')">×</button>
    </header>

    <dl class="participant-details__facts">
      <div>
        <dt>{{ t('participants.startingPoint') }}</dt>
        <dd>{{ coordinate(participant.location) }}</dd>
      </div>
      <div>
        <dt>{{ t('participants.travelToMeeting') }}</dt>
        <dd v-if="travelTime !== undefined" class="participant-details__time">{{ formatMinutes(travelTime, t('results.minutes')) }}</dd>
        <dd v-else class="participant-details__muted">{{ t('participants.noTravelTime') }}</dd>
      </div>
      <div>
        <dt>{{ t('participants.transport') }}</dt>
        <dd>{{ transportLabel }}</dd>
      </div>
      <div v-if="meetingPoint">
        <dt>{{ t('participants.meetingPoint') }}</dt>
        <dd>{{ coordinate(meetingPoint) }}</dd>
      </div>
    </dl>
  </aside>
</template>

<style scoped>
.participant-details { width: min(360px, calc(100vw - 48px)); padding: var(--space-4); border: 1px solid var(--color-border-strong); border-radius: var(--radius-md); background: var(--color-map-overlay); box-shadow: var(--shadow-floating); backdrop-filter: blur(10px); }
.participant-details__header { display: grid; grid-template-columns: 44px minmax(0, 1fr) 28px; gap: var(--space-3); align-items: center; padding-bottom: var(--space-4); border-bottom: 1px solid var(--color-border); }
.participant-details__avatar { display: grid; place-items: center; width: 44px; height: 44px; border-radius: 50%; color: #101725; background: #d9e3ff; font-weight: 800; }
.participant-details h2 { margin: 0; font-family: var(--font-display); font-size: 1.55rem; font-weight: 400; letter-spacing: .035em; }
.participant-details__header p { margin: 2px 0 0; color: var(--color-text-muted); font-size: .72rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.participant-details__close { width: 28px; height: 28px; padding: 0; border: 0; border-radius: 50%; color: var(--color-text-muted); background: transparent; cursor: pointer; font-size: 1.35rem; line-height: 1; }
.participant-details__close:hover { color: var(--color-text); background: var(--color-surface-hover); }
.participant-details__facts { display: grid; gap: var(--space-4); margin: var(--space-4) 0 0; }
.participant-details__facts div { display: grid; gap: 2px; }
.participant-details dt { color: var(--color-text-subtle); font-size: .64rem; font-weight: 750; letter-spacing: .08em; text-transform: uppercase; }
.participant-details dd { margin: 0; color: var(--color-text); font-size: .84rem; font-variant-numeric: tabular-nums; }
.participant-details__time { color: var(--color-success) !important; font-family: var(--font-display); font-size: 1.8rem !important; letter-spacing: .04em; }
.participant-details__muted { color: var(--color-text-muted) !important; }
</style>
