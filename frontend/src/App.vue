<script setup lang="ts">
/**
 * Application shell and flow orchestrator. Establishes the desktop-first layout
 * (header / main content / sidebar), dark mode (applied on the document root),
 * and ES/EN language selection. Wires the create → compute → view flow through
 * the typed API client, surfacing routing failures transparently.
 */
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import LanguageSelector from './components/LanguageSelector.vue'
import MeetingForm from './components/MeetingForm.vue'
import StrategyComparisonView from './components/StrategyComparisonView.vue'
import OutlierTradeoffPanel from './components/OutlierTradeoffPanel.vue'
import RoutingErrorNotice from './components/RoutingErrorNotice.vue'
import {
  RoutingFailureError,
  computeRecommendations,
  createMeeting,
} from './api/meetingApi'
import type { Meeting, MeetingRequest, Recommendation, RoutingFailure } from './types/Meeting'

const { t } = useI18n()

const meeting = ref<Meeting | null>(null)
const recommendation = ref<Recommendation | null>(null)
const routingFailure = ref<RoutingFailure | null>(null)
const errorMessage = ref<string>('')
const submitting = ref(false)

const participantNames = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  for (const participant of meeting.value?.participants ?? []) {
    map[participant.id] = participant.name.length > 0 ? participant.name : participant.id
  }
  return map
})

async function onSubmit(request: MeetingRequest): Promise<void> {
  submitting.value = true
  errorMessage.value = ''
  routingFailure.value = null
  recommendation.value = null
  try {
    const created = await createMeeting(request)
    meeting.value = created
    recommendation.value = await computeRecommendations(created.urlCode)
  } catch (error) {
    if (error instanceof RoutingFailureError) {
      routingFailure.value = error.failure
    } else if (error instanceof Error) {
      errorMessage.value = error.message
    } else {
      errorMessage.value = t('common.error')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="app-container">
    <header class="app-header">
      <div>
        <h1 class="app-title">{{ t('app.title') }}</h1>
        <p class="app-tagline">{{ t('app.tagline') }}</p>
      </div>
      <LanguageSelector />
    </header>

    <main class="app-main">
      <section class="app-content" aria-label="content">
        <StrategyComparisonView
          v-if="recommendation"
          :results="recommendation.results"
          :participant-names="participantNames"
        />
        <OutlierTradeoffPanel
          v-if="recommendation && recommendation.outlierTradeoff"
          :tradeoff="recommendation.outlierTradeoff"
          :participant-names="participantNames"
        />
        <RoutingErrorNotice
          v-else-if="routingFailure"
          :failure="routingFailure"
          :participant-names="participantNames"
        />
        <p v-else-if="errorMessage" class="app-error" role="alert">{{ errorMessage }}</p>
        <p v-else-if="!recommendation" class="app-empty">{{ t('results.empty') }}</p>
      </section>

      <aside class="app-sidebar" aria-label="sidebar">
        <MeetingForm mode="create" :submitting="submitting" @submit="onSubmit" />
      </aside>
    </main>
  </div>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: var(--space-3);
  margin-bottom: var(--space-4);
  border-bottom: 1px solid var(--color-border);
}

.app-title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
}

.app-tagline {
  margin: var(--space-1) 0 0;
  color: var(--color-text-muted);
  font-size: 0.875rem;
  max-width: 48ch;
}

.app-main {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--space-4);
}

@media (max-width: 900px) {
  .app-main {
    grid-template-columns: 1fr;
  }
}

.app-content,
.app-sidebar {
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-4);
  min-height: 240px;
}

.app-empty,
.app-error {
  color: var(--color-text-muted);
}

.app-error {
  color: var(--color-danger);
}
</style>
