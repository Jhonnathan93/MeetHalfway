import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages, type Locale, type MessageSchema } from '@/shared/i18n/messages'
import type { Meeting, MeetingRequest, Recommendation, RoutingFailure } from './types'
import { RoutingFailureError } from '@/shared/api/errors'
import { computeRecommendations, createMeeting } from './meetingApi'
import MeetingView from './MeetingView.vue'

vi.mock('./meetingApi', () => ({
  createMeeting: vi.fn(),
  computeRecommendations: vi.fn(),
}))

const MeetingFormStub = defineComponent({
  name: 'MeetingForm',
  props: { submitting: Boolean },
  emits: ['submit'],
  template: '<button data-testid="meeting-form-stub">Create</button>',
})

const MapViewStub = defineComponent({
  name: 'MapView',
  props: ['phase', 'results', 'participants', 'warnings', 'errorMessage'],
  template: '<div data-testid="map-stub" :data-phase="phase" />',
})

function makeI18n(locale: Locale = 'en') {
  return createI18n<[MessageSchema], Locale>({ legacy: false, locale, fallbackLocale: 'en', messages })
}

const request: MeetingRequest = {
  participants: [{ name: 'Ana', lat: 6.2, lng: -75.57 }, { name: 'Beto', lat: 6.25, lng: -75.6 }],
  transportMode: 'driving',
}
const meeting: Meeting = {
  urlCode: 'ABC12345',
  participants: [{ id: 'p1', name: 'Ana', location: { lat: 6.2, lng: -75.57 } }],
  transportMode: 'driving',
  recommendation: null,
}
const result = {
  point: { lat: 6.22, lng: -75.58 }, perParticipant: { p1: 10 },
  sumTime: 10, maxTime: 10, stdDev: 0,
  candidateId: 'fastest' as const, recommended: true,
}
const recommendation: Recommendation = {
  results: { fastest: result, minimax: { ...result, candidateId: 'minimax' }, fairest: { ...result, candidateId: 'fairest' } },
  outlierTradeoff: null,
  warnings: [],
}

function mountView() {
  return mount(MeetingView, {
    global: { plugins: [makeI18n()], stubs: { MeetingForm: MeetingFormStub, MapView: MapViewStub } },
  })
}

afterEach(() => vi.clearAllMocks())

describe('MeetingView flow', () => {
  it('creates a meeting, computes recommendations, and renders the result', async () => {
    vi.mocked(createMeeting).mockResolvedValueOnce(meeting)
    vi.mocked(computeRecommendations).mockResolvedValueOnce(recommendation)
    const wrapper = mountView()

    wrapper.findComponent(MeetingFormStub).vm.$emit('submit', request)
    await flushPromises()

    expect(createMeeting).toHaveBeenCalledWith(request)
    expect(computeRecommendations).toHaveBeenCalledWith(meeting.urlCode)
    expect(vi.mocked(createMeeting).mock.invocationCallOrder[0]).toBeLessThan(
      vi.mocked(computeRecommendations).mock.invocationCallOrder[0]!,
    )
    expect(wrapper.findAll('.strategy-card')).toHaveLength(3)
    expect(wrapper.find('[data-testid="map-stub"]').attributes('data-phase')).toBe('success')
    expect(wrapper.findComponent(MapViewStub).props('participants')).toEqual(meeting.participants)
  })

  it('shows a routing failure without rendering stale recommendations', async () => {
    const failure: RoutingFailure = {
      code: 'ROUTING_FAILURE',
      message: 'Correct or remove the affected locations and try again.',
      errors: [{ participantId: 'p1', location: meeting.participants[0]!.location, reason: 'No route' }],
    }
    vi.mocked(createMeeting).mockResolvedValueOnce(meeting)
    vi.mocked(computeRecommendations).mockRejectedValueOnce(new RoutingFailureError(failure))
    const wrapper = mountView()

    wrapper.findComponent(MeetingFormStub).vm.$emit('submit', request)
    await flushPromises()

    expect(wrapper.find('.routing-error').exists()).toBe(true)
    expect(wrapper.findAll('.strategy-card')).toHaveLength(0)
    expect(wrapper.find('[data-testid="map-stub"]').attributes('data-phase')).toBe('error')
  })

  it('applies the outlier choice to both recommendations and the map', async () => {
    const meetingWithOutlier: Meeting = {
      ...meeting,
      participants: [
        ...meeting.participants,
        { id: 'p2', name: 'Santiago', location: { lat: 6.3, lng: -75.6 } },
      ],
    }
    const includingResult = {
      ...result,
      perParticipant: { p1: 10, p2: 27 },
      sumTime: 37,
      maxTime: 27,
      stdDev: 8.5,
    }
    const excludingResult = {
      ...result,
      point: { lat: 6.24, lng: -75.59 },
      perParticipant: { p1: 7 },
      sumTime: 7,
      maxTime: 7,
      stdDev: 0,
    }
    const withOutlierTradeoff: Recommendation = {
      results: {
        fastest: includingResult,
        minimax: { ...includingResult, candidateId: 'minimax' },
        fairest: { ...includingResult, candidateId: 'fairest' },
      },
      outlierTradeoff: {
        outliers: ['p2'],
        including: {
          fastest: includingResult,
          minimax: { ...includingResult, candidateId: 'minimax' },
          fairest: { ...includingResult, candidateId: 'fairest' },
        },
        excluding: {
          fastest: excludingResult,
          minimax: { ...excludingResult, candidateId: 'minimax' },
          fairest: { ...excludingResult, candidateId: 'fairest' },
        },
        avgTravelTimeIncluding: 18.5,
        avgTravelTimeExcluding: 7,
      },
      warnings: [],
    }
    vi.mocked(createMeeting).mockResolvedValueOnce(meetingWithOutlier)
    vi.mocked(computeRecommendations).mockResolvedValueOnce(withOutlierTradeoff)
    const wrapper = mountView()

    wrapper.findComponent(MeetingFormStub).vm.$emit('submit', request)
    await flushPromises()
    expect(wrapper.findComponent(MapViewStub).props('participants')).toHaveLength(2)
    expect(wrapper.get('[data-testid="outlier-choose-excluding"]').text()).toContain('Santiago')

    await wrapper.get('[data-testid="outlier-choose-excluding"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('.strategy-card').text()).toContain('7 min')
    expect(wrapper.find('.strategy-card').text()).not.toContain('Santiago')
    expect(wrapper.findComponent(MapViewStub).props('results').fastest.point).toEqual({
      lat: 6.24,
      lng: -75.59,
    })
    expect(wrapper.findComponent(MapViewStub).props('participants')).toEqual([meetingWithOutlier.participants[0]])

    await wrapper.get('[data-testid="outlier-choose-including"]').trigger('click')
    await flushPromises()

    expect(wrapper.findComponent(MapViewStub).props('results').fastest.point).toEqual(includingResult.point)
    expect(wrapper.findComponent(MapViewStub).props('participants')).toHaveLength(2)
  })
})
