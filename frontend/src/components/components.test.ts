import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages, type Locale, type MessageSchema } from '@/i18n/messages'
import { applyDefaultTheme } from '@/composables/useTheme'
import StrategyComparisonView from './StrategyComparisonView.vue'
import OutlierTradeoffPanel from './OutlierTradeoffPanel.vue'
import LanguageSelector from './LanguageSelector.vue'
import AddressSearchBox from './AddressSearchBox.vue'
import MeetingForm from './MeetingForm.vue'
import * as geocoding from '@/api/geocoding'
import type {
  Coordinate,
  MeetingRequest,
  OutlierTradeoff,
  StrategyKey,
  StrategyResult,
  StrategyResults,
} from '@/types/Meeting'

function makeI18n(locale: Locale = 'es') {
  return createI18n<[MessageSchema], Locale>({
    legacy: false,
    locale,
    fallbackLocale: 'en',
    messages,
  })
}

function strategy(sum: number, candidateId: StrategyKey): StrategyResult {
  return {
    point: { lat: 6.25, lng: -75.56 },
    perParticipant: { a: 10, b: 20 },
    sumTime: sum,
    maxTime: 20,
    stdDev: 5,
    candidateId,
    recommended: true,
  }
}

const results: StrategyResults = {
  fastest: strategy(30, 'fastest'),
  minimax: strategy(32, 'minimax'),
  fairest: strategy(34, 'fairest'),
}

describe('StrategyComparisonView', () => {
  it('renders all three strategies with metrics', () => {
    const wrapper = mount(StrategyComparisonView, {
      global: { plugins: [makeI18n('en')] },
      props: { results, participantNames: { a: 'Ana', b: 'Beto' } },
    })
    const cards = wrapper.findAll('.strategy-card')
    expect(cards).toHaveLength(3)
    expect(wrapper.find('[data-strategy="fastest"]').exists()).toBe(true)
    expect(wrapper.find('[data-strategy="minimax"]').exists()).toBe(true)
    expect(wrapper.find('[data-strategy="fairest"]').exists()).toBe(true)
    // Participant names are shown per strategy.
    expect(wrapper.text()).toContain('Ana')
    expect(wrapper.text()).toContain('Beto')
  })
})

describe('OutlierTradeoffPanel', () => {
  it('shows both include and exclude sides and never auto-excludes', () => {
    const tradeoff: OutlierTradeoff = {
      outliers: ['a'],
      including: results,
      excluding: results,
      avgTravelTimeIncluding: 15,
      avgTravelTimeExcluding: 10,
    }
    const wrapper = mount(OutlierTradeoffPanel, {
      global: { plugins: [makeI18n('en')] },
      props: { tradeoff, participantNames: { a: 'Ana' } },
    })
    expect(wrapper.find('[data-variant="including"]').exists()).toBe(true)
    expect(wrapper.find('[data-variant="excluding"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Ana')
  })
})

describe('LanguageSelector (i18n switching)', () => {
  it('switches visible copy between ES and EN', async () => {
    const i18n = makeI18n('es')
    const wrapper = mount(LanguageSelector, { global: { plugins: [i18n] } })
    // ES default: the Spanish label for the language control.
    expect(wrapper.text()).toContain('Idioma')
    // Switch to English via the select element (drives the component's handler).
    const select = wrapper.find('select')
    await select.setValue('en')
    expect(wrapper.text()).toContain('Language')
  })
})

describe('AddressSearchBox (no map-click)', () => {
  it('uses a text input with autocomplete and has no map/click-to-pick element', () => {
    const wrapper = mount(AddressSearchBox, {
      global: { plugins: [makeI18n('en')] },
      props: { modelValue: '' },
    })
    expect(wrapper.find('input[type="text"]').exists()).toBe(true)
    // No map affordance of any kind (Req 9.8).
    expect(wrapper.find('.map, [data-map], canvas').exists()).toBe(false)
  })
})

describe('dark mode default', () => {
  beforeEach(() => {
    document.documentElement.removeAttribute('data-theme')
  })

  it('applies data-theme="dark" on the document root', () => {
    applyDefaultTheme()
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })
})

describe('MeetingForm address resolution', () => {
  it('resolves a selected suggestion to real coordinates and submits them', async () => {
    const coordinate: Coordinate = { lat: 6.21, lng: -75.57 }
    vi.spyOn(geocoding, 'resolveAddress').mockResolvedValue(coordinate)

    const wrapper = mount(MeetingForm, {
      global: { plugins: [makeI18n('en')] },
      props: { mode: 'create' as const, submitting: false },
    })

    const boxes = wrapper.findAllComponents(AddressSearchBox)
    expect(boxes.length).toBeGreaterThanOrEqual(2)
    // Simulate choosing a suggestion in each participant's search box.
    for (const box of boxes) {
      box.vm.$emit('select', { description: 'El Poblado, Medellin', placeId: 'p1' })
    }
    await wrapper.vm.$nextTick()
    await Promise.resolve()

    await wrapper.find('form').trigger('submit.prevent')

    const emitted = wrapper.emitted('submit')
    expect(emitted).toBeTruthy()
    const request = emitted?.[0]?.[0] as MeetingRequest
    expect(request.participants.every((p) => p.lat === 6.21 && p.lng === -75.57)).toBe(true)
    expect(request.transportMode).toBe('driving')
  })

  it('does not fabricate a location when resolution fails', async () => {
    vi.spyOn(geocoding, 'resolveAddress').mockResolvedValue(null)

    const wrapper = mount(MeetingForm, {
      global: { plugins: [makeI18n('en')] },
      props: { mode: 'create' as const, submitting: false },
    })

    const box = wrapper.findComponent(AddressSearchBox)
    box.vm.$emit('select', { description: 'nowhere', placeId: 'x' })
    await wrapper.vm.$nextTick()
    await Promise.resolve()

    await wrapper.find('form').trigger('submit.prevent')

    // No submit emitted because coordinates were never populated (no fabrication).
    expect(wrapper.emitted('submit')).toBeFalsy()
  })
})
