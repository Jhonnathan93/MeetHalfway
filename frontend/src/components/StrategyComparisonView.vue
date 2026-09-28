<script setup lang="ts">
/**
 * Renders all three strategy results (Fastest, Minimax, Fairest) side by side,
 * each with its point coordinate, per-participant travel time, and the Σ / max /
 * σ metrics (Requirement 8). The Fairest hint explains balance in terms of real
 * travel time and explicitly avoids implying a geographic midpoint is "fair".
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { STRATEGY_KEYS, type StrategyKey, type StrategyResults } from '@/types/Meeting'
import { formatMetric, formatMinutes } from '@/utils/formatMinutes'

const props = defineProps<{
  results: StrategyResults
  /** Optional participant id -> display name for friendlier labels. */
  participantNames: Record<string, string>
}>()

const { t } = useI18n()

interface StrategyCard {
  key: StrategyKey
  title: string
  hint: string
  point: { lat: number; lng: number }
  perParticipant: Array<{ id: string; label: string; minutes: string }>
  sumTime: string
  maxTime: string
  stdDev: string
}

const minutesUnit = computed(() => t('results.minutes'))

const cards = computed<StrategyCard[]>(() =>
  STRATEGY_KEYS.map((key) => {
    const result = props.results[key]
    return {
      key,
      title: t(`results.${key}`),
      hint: t(`results.${key}Hint`),
      point: result.point,
      perParticipant: Object.entries(result.perParticipant).map(([id, minutes]) => ({
        id,
        label: props.participantNames[id] ?? id,
        minutes: formatMinutes(minutes, minutesUnit.value),
      })),
      sumTime: formatMetric(result.sumTime),
      maxTime: formatMinutes(result.maxTime, minutesUnit.value),
      stdDev: formatMetric(result.stdDev),
    }
  }),
)
</script>

<template>
  <section class="strategy-comparison" aria-label="results">
    <h2 class="strategy-comparison__heading">{{ t('results.heading') }}</h2>
    <div class="strategy-comparison__grid">
      <article
        v-for="card in cards"
        :key="card.key"
        class="strategy-card"
        :data-strategy="card.key"
      >
        <header class="strategy-card__header">
          <h3 class="strategy-card__title">{{ card.title }}</h3>
          <p class="strategy-card__hint">{{ card.hint }}</p>
        </header>

        <dl class="strategy-card__metrics">
          <div class="strategy-card__metric">
            <dt>{{ t('results.point') }}</dt>
            <dd>{{ card.point.lat.toFixed(5) }}, {{ card.point.lng.toFixed(5) }}</dd>
          </div>
          <div class="strategy-card__metric">
            <dt>{{ t('results.sumTime') }}</dt>
            <dd>{{ card.sumTime }} {{ minutesUnit }}</dd>
          </div>
          <div class="strategy-card__metric">
            <dt>{{ t('results.maxTime') }}</dt>
            <dd>{{ card.maxTime }}</dd>
          </div>
          <div class="strategy-card__metric">
            <dt>{{ t('results.stdDev') }}</dt>
            <dd>{{ card.stdDev }}</dd>
          </div>
        </dl>

        <div class="strategy-card__participants">
          <h4 class="strategy-card__subtitle">{{ t('results.perParticipant') }}</h4>
          <ul>
            <li v-for="entry in card.perParticipant" :key="entry.id">
              <span class="strategy-card__participant-name">{{ entry.label }}</span>
              <span class="strategy-card__participant-time">{{ entry.minutes }}</span>
            </li>
          </ul>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.strategy-comparison__heading {
  margin: 0 0 var(--space-3);
  font-size: 1.25rem;
}

.strategy-comparison__grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-3);
}

@media (max-width: 900px) {
  .strategy-comparison__grid {
    grid-template-columns: 1fr;
  }
}

.strategy-card {
  background-color: var(--color-surface-raised);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-3);
}

.strategy-card__title {
  margin: 0 0 var(--space-1);
  font-size: 1.05rem;
  color: var(--color-primary);
}

.strategy-card__hint {
  margin: 0 0 var(--space-3);
  color: var(--color-text-muted);
  font-size: 0.8125rem;
}

.strategy-card__metrics {
  margin: 0 0 var(--space-3);
  display: grid;
  gap: var(--space-1);
}

.strategy-card__metric {
  display: flex;
  justify-content: space-between;
  gap: var(--space-2);
}

.strategy-card__metric dt {
  color: var(--color-text-muted);
  font-size: 0.8125rem;
}

.strategy-card__metric dd {
  margin: 0;
  font-variant-numeric: tabular-nums;
}

.strategy-card__subtitle {
  margin: 0 0 var(--space-1);
  font-size: 0.8125rem;
  color: var(--color-text-muted);
}

.strategy-card__participants ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 2px;
}

.strategy-card__participants li {
  display: flex;
  justify-content: space-between;
  gap: var(--space-2);
}

.strategy-card__participant-time {
  font-variant-numeric: tabular-nums;
}
</style>
