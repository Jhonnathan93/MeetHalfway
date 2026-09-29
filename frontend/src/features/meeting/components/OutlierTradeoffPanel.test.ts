import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages, type Locale, type MessageSchema } from '@/shared/i18n/messages'
import OutlierTradeoffPanel from './OutlierTradeoffPanel.vue'
import type { OutlierTradeoff, StrategyKey, StrategyResult, StrategyResults } from '../types'

function makeI18n(locale: Locale = 'en') {
  return createI18n<[MessageSchema], Locale>({ legacy: false, locale, fallbackLocale: 'en', messages })
}

function result(candidateId: StrategyKey): StrategyResult {
  return {
    point: { lat: 6.25, lng: -75.56 }, perParticipant: { a: 10, b: 20 },
    sumTime: 30, maxTime: 20, stdDev: 5, candidateId, recommended: true,
  }
}

const results: StrategyResults = {
  fastest: result('fastest'), minimax: result('minimax'), fairest: result('fairest'),
}

describe('OutlierTradeoffPanel', () => {
  it('shows both variants without automatically excluding participants', () => {
    const tradeoff: OutlierTradeoff = {
      outliers: ['a'], including: results, excluding: results,
      avgTravelTimeIncluding: 15, avgTravelTimeExcluding: 10,
    }
    const wrapper = mount(OutlierTradeoffPanel, {
      global: { plugins: [makeI18n()] },
      props: { tradeoff, participantNames: { a: 'Ana' } },
    })

    expect(wrapper.find('[data-variant="including"]').exists()).toBe(true)
    expect(wrapper.find('[data-variant="excluding"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Ana')
    expect(wrapper.get('[data-testid="outlier-choose-including"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-testid="outlier-choose-excluding"]').attributes('aria-pressed')).toBe('false')
  })

  it('emits the requested participant set when a choice is clicked', async () => {
    const tradeoff: OutlierTradeoff = {
      outliers: ['a'], including: results, excluding: results,
      avgTravelTimeIncluding: 15, avgTravelTimeExcluding: 10,
    }
    const wrapper = mount(OutlierTradeoffPanel, {
      global: { plugins: [makeI18n()] },
      props: { tradeoff, participantNames: { a: 'Ana' } },
    })

    await wrapper.get('[data-testid="outlier-choose-excluding"]').trigger('click')

    expect(wrapper.emitted('select-variant')).toEqual([['excluding']])
  })
})
