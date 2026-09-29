import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages, type Locale, type MessageSchema } from '@/shared/i18n/messages'
import StrategyComparisonView from './StrategyComparisonView.vue'
import type { StrategyKey, StrategyResult, StrategyResults } from '../types'

function makeI18n(locale: Locale = 'en') {
  return createI18n<[MessageSchema], Locale>({ legacy: false, locale, fallbackLocale: 'en', messages })
}

function result(sumTime: number, candidateId: StrategyKey): StrategyResult {
  return {
    point: { lat: 6.25, lng: -75.56 }, perParticipant: { a: 10, b: 20 },
    sumTime, maxTime: 20, stdDev: 5, candidateId, recommended: true,
  }
}

const results: StrategyResults = {
  fastest: result(30, 'fastest'),
  minimax: result(32, 'minimax'),
  fairest: result(34, 'fairest'),
}

describe('StrategyComparisonView', () => {
  it('renders all strategies, metrics, and participant names', () => {
    const wrapper = mount(StrategyComparisonView, {
      global: { plugins: [makeI18n()] },
      props: { results, participantNames: { a: 'Ana', b: 'Beto' } },
    })

    expect(wrapper.findAll('.strategy-card')).toHaveLength(3)
    expect(wrapper.find('[data-strategy="fastest"]').exists()).toBe(true)
    expect(wrapper.find('[data-strategy="minimax"]').exists()).toBe(true)
    expect(wrapper.find('[data-strategy="fairest"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Ana')
    expect(wrapper.text()).toContain('Beto')
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })

  it('explains when all three strategies converge on identical results', () => {
    const convergedResult = result(30, 'fastest')
    const converged: StrategyResults = {
      fastest: convergedResult,
      minimax: { ...convergedResult, candidateId: 'minimax' },
      fairest: { ...convergedResult, candidateId: 'fairest' },
    }
    const wrapper = mount(StrategyComparisonView, {
      global: { plugins: [makeI18n()] },
      props: { results: converged, participantNames: {} },
    })

    expect(wrapper.find('[role="status"]').text()).toContain('All three strategies selected the same point')
  })
})
