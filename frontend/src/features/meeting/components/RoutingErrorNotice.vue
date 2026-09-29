<script setup lang="ts">
/**
 * Surfaces a routing failure (HTTP 422) transparently (Requirement 6): each
 * affected participant location is listed with its specific reason and a clear
 * correct/remove instruction. No participant is hidden and no result is
 * fabricated.
 */
import { useI18n } from 'vue-i18n'
import type { RoutingFailure } from '@/features/meeting/types'

defineProps<{
  failure: RoutingFailure
  /** Optional participant id -> display name for friendlier labels. */
  participantNames: Record<string, string>
}>()

const { t } = useI18n()
</script>

<template>
  <section class="routing-error" role="alert" aria-label="routing-error">
    <h2 class="routing-error__heading">{{ t('routingError.heading') }}</h2>
    <p class="routing-error__message">{{ failure.message }}</p>
    <ul class="routing-error__list">
      <li v-for="(err, index) in failure.errors" :key="`${err.participantId}-${index}`">
        <strong class="routing-error__who">
          {{ participantNames[err.participantId] ?? err.participantId }}
        </strong>
        <span class="routing-error__coord">
          ({{ err.location.lat.toFixed(5) }}, {{ err.location.lng.toFixed(5) }})
        </span>
        <span class="routing-error__reason">{{ t('routingError.reason') }}: {{ err.reason }}</span>
        <span class="routing-error__action">{{ t('routingError.action') }}</span>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.routing-error {
  background-color: var(--color-surface-raised);
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  margin-top: var(--space-3);
}

.routing-error__heading {
  margin: 0 0 var(--space-2);
  font-size: 1.1rem;
  color: var(--color-danger);
}

.routing-error__message {
  margin: 0 0 var(--space-2);
}

.routing-error__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--space-2);
}

.routing-error__list li {
  display: flex;
  flex-direction: column;
  gap: 2px;
  border-top: 1px solid var(--color-border);
  padding-top: var(--space-2);
}

.routing-error__coord {
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
  font-size: 0.8125rem;
}

.routing-error__action {
  color: var(--color-text-muted);
  font-size: 0.8125rem;
}
</style>
