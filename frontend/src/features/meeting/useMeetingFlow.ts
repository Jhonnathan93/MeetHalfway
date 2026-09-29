import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { computeRecommendations, createMeeting } from './meetingApi'
import { RoutingFailureError } from '@/shared/api/errors'
import type {
  Meeting,
  MeetingRequest,
  OutlierVariant,
  Recommendation,
  RoutingFailure,
  StrategyResults,
} from './types'

export type MapPhase = 'loading' | 'error' | 'success'

/** Owns the create → recommend flow and the state shown by the meeting view. */
export function useMeetingFlow() {
  const { t } = useI18n()
  const meeting = ref<Meeting | null>(null)
  const recommendation = ref<Recommendation | null>(null)
  const routingFailure = ref<RoutingFailure | null>(null)
  const errorMessage = ref('')
  const submitting = ref(false)
  const outlierVariant = ref<OutlierVariant>('including')
  const selectedParticipantId = ref<string | null>(null)

  const displayResults = computed<StrategyResults | null>(() => {
    const tradeoff = recommendation.value?.outlierTradeoff
    if (!recommendation.value) return null
    if (!tradeoff) return recommendation.value.results
    return tradeoff[outlierVariant.value]
  })

  const displayParticipants = computed(() => {
    const participantIds = new Set(Object.keys(displayResults.value?.fastest.perParticipant ?? {}))
    return (meeting.value?.participants ?? []).filter(({ id }) => participantIds.has(id))
  })

  const participantNames = computed<Record<string, string>>(() => {
    const names: Record<string, string> = {}
    for (const participant of meeting.value?.participants ?? []) {
      names[participant.id] = participant.name || participant.id
    }
    return names
  })

  const mapPhase = computed<MapPhase>(() => {
    if (submitting.value) return 'loading'
    if (errorMessage.value || routingFailure.value) return 'error'
    return 'success'
  })

  const mapErrorMessage = computed(() => errorMessage.value || routingFailure.value?.message || '')

  async function submitMeeting(request: MeetingRequest): Promise<void> {
    submitting.value = true
    errorMessage.value = ''
    routingFailure.value = null
    recommendation.value = null
    outlierVariant.value = 'including'
    selectedParticipantId.value = null
    try {
      const created = await createMeeting(request)
      meeting.value = created
      recommendation.value = await computeRecommendations(created.urlCode)
    } catch (error) {
      if (error instanceof RoutingFailureError) {
        routingFailure.value = error.failure
      } else {
        errorMessage.value = error instanceof Error ? error.message : t('common.error')
      }
    } finally {
      submitting.value = false
    }
  }

  return {
    meeting,
    recommendation,
    outlierVariant,
    selectedParticipantId,
    displayResults,
    displayParticipants,
    selectOutlierVariant: (variant: OutlierVariant) => {
      outlierVariant.value = variant
      if (selectedParticipantId.value && !displayParticipants.value.some(
        ({ id }) => id === selectedParticipantId.value,
      )) {
        selectedParticipantId.value = null
      }
    },
    selectParticipant: (participantId: string) => {
      selectedParticipantId.value = participantId
    },
    clearSelectedParticipant: () => {
      selectedParticipantId.value = null
    },
    routingFailure,
    errorMessage,
    submitting,
    participantNames,
    mapPhase,
    mapErrorMessage,
    submitMeeting,
  }
}
