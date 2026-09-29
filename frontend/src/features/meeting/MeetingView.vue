<script setup lang="ts">
import MeetingForm from './components/MeetingForm.vue'
import StrategyComparisonView from './components/StrategyComparisonView.vue'
import OutlierTradeoffPanel from './components/OutlierTradeoffPanel.vue'
import RoutingErrorNotice from './components/RoutingErrorNotice.vue'
import MapView from './map/MapView.vue'
import { useMeetingFlow } from './useMeetingFlow'

const {
  recommendation,
  outlierVariant,
  displayResults,
  displayParticipants,
  routingFailure,
  errorMessage,
  submitting,
  participantNames,
  mapPhase,
  mapErrorMessage,
  submitMeeting,
  selectOutlierVariant,
} = useMeetingFlow()
</script>

<template>
  <main class="meeting-layout">
    <section class="meeting-content" aria-label="content">
      <StrategyComparisonView
        v-if="displayResults"
        :results="displayResults"
        :participant-names="participantNames"
      />
      <OutlierTradeoffPanel
        v-if="recommendation && recommendation.outlierTradeoff"
        :tradeoff="recommendation.outlierTradeoff"
        :participant-names="participantNames"
        :selected-variant="outlierVariant"
        @select-variant="selectOutlierVariant"
      />
      <RoutingErrorNotice
        v-else-if="routingFailure"
        :failure="routingFailure"
        :participant-names="participantNames"
      />
      <p v-else-if="errorMessage" class="meeting-error" role="alert">{{ errorMessage }}</p>
      <p v-else-if="!recommendation" class="meeting-empty">{{ $t('results.empty') }}</p>

      <MapView
        :phase="mapPhase"
        :results="displayResults"
        :participants="displayParticipants"
        :warnings="recommendation ? recommendation.warnings : []"
        :error-message="mapErrorMessage"
      />
    </section>

    <aside class="meeting-sidebar" aria-label="sidebar">
      <MeetingForm :submitting="submitting" @submit="submitMeeting" />
    </aside>
  </main>
</template>

<style scoped>
.meeting-layout {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--space-4);
}

.meeting-content,
.meeting-sidebar {
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-4);
  min-height: 240px;
}

.meeting-empty,
.meeting-error {
  color: var(--color-text-muted);
}

.meeting-error {
  color: var(--color-danger);
}

@media (max-width: 900px) {
  .meeting-layout {
    grid-template-columns: 1fr;
  }
}
</style>
