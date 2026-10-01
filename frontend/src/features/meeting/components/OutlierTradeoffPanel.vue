<script setup lang="ts">
/**
 * Presents the include-vs-exclude outlier trade-off transparently
 * (Requirement 7). Shows both computations with their group average travel
 * times; the user decides which participant set drives the displayed results
 * and map. The meeting itself is never mutated.
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { OutlierTradeoff, OutlierVariant } from '@/features/meeting/types'
import { formatMetric } from '@/shared/formatting/formatMinutes'

const props = withDefaults(defineProps<{
  tradeoff: OutlierTradeoff
  /** Optional participant id -> display name for friendlier labels. */
  participantNames: Record<string, string>
  selectedVariant?: OutlierVariant
}>(), { selectedVariant: 'including' })

const emit = defineEmits<{
  'select-variant': [variant: OutlierVariant]
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
      <article
        class="outlier-panel__side"
        :class="{ 'outlier-panel__side--selected': selectedVariant === 'including' }"
        data-variant="including"
      >
        <h3>{{ t('outlier.including') }}</h3>
        <p class="outlier-panel__avg">
          {{ t('outlier.avgIncluding') }}: {{ avgIncluding }} {{ t('results.minutes') }}
        </p>
        <button
          class="outlier-panel__button"
          type="button"
          :aria-pressed="selectedVariant === 'including'"
          data-testid="outlier-choose-including"
          @click="emit('select-variant', 'including')"
        >
          {{ selectedVariant === 'including' ? t('outlier.selected') : t('outlier.useIncluding') }}
        </button>
      </article>
      <article
        class="outlier-panel__side"
        :class="{ 'outlier-panel__side--selected': selectedVariant === 'excluding' }"
        data-variant="excluding"
      >
        <h3>{{ t('outlier.excluding') }}</h3>
        <p class="outlier-panel__avg">
          {{ t('outlier.avgExcluding') }}: {{ avgExcluding }} {{ t('results.minutes') }}
        </p>
        <button
          class="outlier-panel__button"
          type="button"
          :aria-pressed="selectedVariant === 'excluding'"
          data-testid="outlier-choose-excluding"
          @click="emit('select-variant', 'excluding')"
        >
          {{
            selectedVariant === 'excluding'
              ? t('outlier.selected')
              : t('outlier.useExcluding', { names: outlierLabels.join(', ') })
          }}
        </button>
      </article>
    </div>

    <p class="outlier-panel__choose">{{ t('outlier.choose') }}</p>
  </section>
</template>

<style scoped>
.outlier-panel {
  border-top: 2px solid var(--color-border-strong);
  padding-top: var(--space-4);
  margin-top: var(--space-5);
}

.outlier-panel__heading {
  margin: 0 0 var(--space-2);
  font-size: 1.05rem;
  font-weight: 760;
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
  gap: 0;
  border-top: 1px solid var(--color-border);
}

@media (max-width: 700px) {
  .outlier-panel__grid {
    grid-template-columns: 1fr;
  }
  .outlier-panel__side, .outlier-panel__side:last-child { border-right: 0; }
}

.outlier-panel__side {
  border-right: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
  padding: var(--space-3);
}
.outlier-panel__side:last-child { border-right: 0; }

.outlier-panel__side--selected {
  background: var(--color-primary-soft);
}

.outlier-panel__button {
  margin-top: var(--space-2);
  padding: var(--space-1) var(--space-2);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
}

.outlier-panel__button[aria-pressed='true'] {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: white;
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
