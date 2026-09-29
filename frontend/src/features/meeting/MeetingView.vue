<script setup lang="ts">
import { computed, ref } from 'vue'
import MeetingForm from './components/MeetingForm.vue'
import StrategyComparisonView from './components/StrategyComparisonView.vue'
import OutlierTradeoffPanel from './components/OutlierTradeoffPanel.vue'
import RoutingErrorNotice from './components/RoutingErrorNotice.vue'
import ParticipantList from './components/ParticipantList.vue'
import ParticipantDetails from './components/ParticipantDetails.vue'
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
  selectedParticipantId,
  selectParticipant,
  clearSelectedParticipant,
  meeting,
} = useMeetingFlow()

const sidebarOpen = ref(false)
const selectedParticipant = computed(() =>
  displayParticipants.value.find(({ id }) => id === selectedParticipantId.value) ?? null,
)
</script>

<template>
  <main id="workspace" class="meeting-workspace">
    <aside class="meeting-sidebar" :class="{ 'meeting-sidebar--open': sidebarOpen }" aria-label="sidebar">
      <div class="meeting-sidebar__intro">
        <p>{{ $t('app.tagline') }}</p>
      </div>
      <ParticipantList
        :participants="displayParticipants"
        :selected-participant-id="selectedParticipantId"
        @select="selectParticipant"
      />
      <MeetingForm :submitting="submitting" @submit="submitMeeting" />
    </aside>

    <section class="meeting-map-area" aria-label="content">
      <button
        type="button"
        class="meeting-sidebar-toggle"
        :aria-expanded="sidebarOpen"
        :aria-label="sidebarOpen ? $t('sidebar.close') : $t('sidebar.open')"
        @click="sidebarOpen = !sidebarOpen"
      >
        <span aria-hidden="true">☰</span>
        {{ $t('participants.heading') }}
      </button>
      <MapView
        :phase="mapPhase"
        :results="displayResults"
        :participants="displayParticipants"
        :warnings="recommendation ? recommendation.warnings : []"
        :error-message="mapErrorMessage"
        :selected-participant-id="selectedParticipantId"
        @select-participant="selectParticipant"
      />

      <section v-if="displayResults || routingFailure || errorMessage" class="meeting-insights" aria-label="meeting-insights">
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
      </section>

      <ParticipantDetails
        v-if="selectedParticipant && meeting"
        class="meeting-participant-details"
        :participant="selectedParticipant"
        :results="displayResults"
        :transport-mode="meeting.transportMode"
        @close="clearSelectedParticipant"
      />
    </section>
  </main>
</template>

<style scoped>
.meeting-workspace { min-height: 0; display: grid; grid-template-columns: minmax(288px, 350px) minmax(0, 1fr); overflow: hidden; }
.meeting-sidebar { z-index: 5; overflow-y: auto; padding: var(--space-5); border-right: 1px solid var(--color-border); background: var(--color-surface); }
.meeting-sidebar__intro p { margin: 0 0 var(--space-5); color: var(--color-text-muted); font-size: .82rem; line-height: 1.55; }
.meeting-map-area { position: relative; min-height: 0; background: var(--color-bg); }
.meeting-insights { position: absolute; z-index: 550; left: var(--space-4); bottom: var(--space-4); width: min(660px, calc(100% - 32px)); max-height: min(43vh, 430px); overflow-y: auto; padding: var(--space-4); border: 1px solid var(--color-border); border-radius: var(--radius-md); background: var(--color-map-overlay); box-shadow: var(--shadow-floating); backdrop-filter: blur(10px); }
.meeting-participant-details { position: absolute; z-index: 600; top: var(--space-4); right: var(--space-4); }
.meeting-error { margin: 0; color: var(--color-danger); }

@media (max-width: 850px) {
  .meeting-workspace { grid-template-columns: 280px minmax(0, 1fr); }
  .meeting-sidebar { padding: var(--space-4); }
}
@media (max-width: 700px) {
  .meeting-workspace { display: block; overflow: hidden; }
  .meeting-map-area { height: calc(100dvh - 60px); min-height: 460px; }
  .meeting-sidebar { position: fixed; z-index: 800; right: 0; bottom: 0; left: 0; max-height: min(78dvh, 680px); padding: var(--space-5) var(--space-4); border: 1px solid var(--color-border); border-radius: var(--radius-lg) var(--radius-lg) 0 0; box-shadow: var(--shadow-floating); transform: translateY(calc(100% - 64px)); transition: transform 180ms ease; }
  .meeting-sidebar--open { transform: translateY(0); }
  .meeting-sidebar__intro { display: none; }
  .meeting-sidebar::before { content: ''; display: block; width: 36px; height: 4px; margin: 0 auto var(--space-4); border-radius: 99px; background: var(--color-border-strong); }
  .meeting-insights { max-height: 42vh; }
  .meeting-participant-details { top: auto; right: var(--space-3); bottom: var(--space-3); }
  .meeting-sidebar-toggle { position: absolute; z-index: 560; top: var(--space-3); left: var(--space-3); display: inline-flex; align-items: center; gap: var(--space-2); min-height: 36px; border: 1px solid var(--color-border); border-radius: var(--radius-sm); padding: 0 var(--space-3); color: var(--color-text); background: var(--color-map-overlay); box-shadow: var(--shadow-panel); font-size: .78rem; font-weight: 700; }
}

@media (min-width: 701px) { .meeting-sidebar-toggle { display: none; } }
</style>
