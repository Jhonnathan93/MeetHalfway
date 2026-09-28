<script setup lang="ts">
/**
 * Presents the include-vs-exclude outlier trade-off transparently
 * (Requirement 7). Shows both computations with their group average travel
 * times; the user decides who to include. No one is excluded automatically —
 * this panel only compares, it never mutates the participant set.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { OutlierTradeoff } from '@/types/Meeting'
import { formatMetric } from '@/utils/formatMinutes'

const props = defineProps<{
  tradeoff: OutlierTradeoff
  /** Optional participant id -> display name for friendlier labels. */
  participantNames: Record<string, string>
}>()

const { t } = useI18n()

const outlierLabels = computed(() =>
  props.tradeoff.outliers.map((id) => props.participantNames[id] ?? id),
)

const avgIncluding = computed(() => formatMetric(props.tradeoff.avgTravelTimeIncluding))
const avgExcluding = computed(() => formatMetric(props.tradeoff.avgTravelTimeExcluding))
</script>

<template>
  <section class="outlier-panel" aria-label="outlier-tradeoff">
    <h2 class="outlier-panel__heading">{{ t('outlier.heading') }}</h2>
    <p class="outlier-panel__explanation">{{ t('outlier.explanation') }}</p>

    <ul class="outlier-panel__outliers">
      <li v-for="label in outlierLabels" :key="label">{{ label }}</li>
    </ul>

    <div class="outlier-panel__grid">
      <article class="outlier-panel__side" data-variant="including">
        <h3>{{ t('outlier.including') }}</h3>
        <p class="outlier-panel__avg">
          {{ t('outlier.avgIncluding') }}: {{ avgIncluding }} {{ t('results.minutes') }}
        </p>
      </article>
      <article class="outlier-panel__side" data-variant="excluding">
        <h3>{{ t('outlier.excluding') }}</h3>
        <p class="outlier-panel__avg">
          {{ t('outlier.avgExcluding') }}: {{ avgExcluding }} {{ t('results.minutes') }}
        </p>
      </article>
    </div>

    <p class="outlier-panel__choose">{{ t('outlier.choose') }}</p>
  </section>
</template>

<style scoped>
.outlier-panel {
  background-color: var(--color-surface-raised);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  margin-top: var(--space-4);
}

.outlier-panel__heading {
  margin: 0 0 var(--space-2);
  font-size: 1.15rem;
}

.outlier-panel__explanation {
  margin: 0 0 var(--space-2);
  color: var(--color-text-muted);
  font-size: 0.875rem;
}

.outlier-panel__outliers {
  margin: 0 0 var(--space-3);
  padding-left: var(--space-4);
  color: var(--color-danger);
}

.outlier-panel__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
}

@media (max-width: 700px) {
  .outlier-panel__grid {
    grid-template-columns: 1fr;
  }
}

.outlier-panel__side {
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-3);
}

.outlier-panel__side h3 {
  margin: 0 0 var(--space-1);
  font-size: 1rem;
}

.outlier-panel__avg {
  margin: 0;
  font-variant-numeric: tabular-nums;
}

.outlier-panel__choose {
  margin: var(--space-3) 0 0;
  color: var(--color-text-muted);
  font-size: 0.8125rem;
}
</style>
