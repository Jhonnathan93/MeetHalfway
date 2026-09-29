import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages, type Locale, type MessageSchema } from '@/shared/i18n/messages'
import * as geocoding from '../geocodingApi'
import AddressSearchBox from './AddressSearchBox.vue'

function makeI18n(locale: Locale = 'en') {
  return createI18n<[MessageSchema], Locale>({ legacy: false, locale, fallbackLocale: 'en', messages })
}

afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('AddressSearchBox', () => {
  it('offers text autocomplete and no map-click selection', () => {
    const wrapper = mount(AddressSearchBox, {
      global: { plugins: [makeI18n()] }, props: { modelValue: '' },
    })
    expect(wrapper.find('input[type="text"]').exists()).toBe(true)
    expect(wrapper.find('.map, [data-map], canvas').exists()).toBe(false)
  })

  it('does not show the no-results message after choosing a suggestion', async () => {
    vi.useFakeTimers()
    vi.spyOn(geocoding, 'autocompleteAddress').mockResolvedValue([
      { description: 'Universidad EAFIT, Medellín', placeId: 'W123' },
    ])
    const wrapper = mount(AddressSearchBox, {
      global: { plugins: [makeI18n()] }, props: { modelValue: '' },
    })

    await wrapper.find('input').setValue('Universidad EAFIT')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    await wrapper.find('button.address-search__option').trigger('click')

    expect(wrapper.find('.address-search__status').exists()).toBe(false)
    expect(wrapper.emitted('select')?.[0]?.[0]).toEqual({
      description: 'Universidad EAFIT, Medellín', placeId: 'W123',
    })
  })

  it('aborts and ignores an older response when the query changes', async () => {
    vi.useFakeTimers()
    const pending: Array<(suggestions: { description: string; placeId: string }[]) => void> = []
    const signals: AbortSignal[] = []
    vi.spyOn(geocoding, 'autocompleteAddress').mockImplementation((_query, signal) => {
      if (signal) signals.push(signal)
      return new Promise((resolve) => pending.push(resolve))
    })
    const wrapper = mount(AddressSearchBox, {
      global: { plugins: [makeI18n()] }, props: { modelValue: '' },
    })
    const input = wrapper.find('input')

    await input.setValue('Old query')
    await vi.advanceTimersByTimeAsync(300)
    expect(pending).toHaveLength(1)

    await input.setValue('Current query')
    expect(signals[0]?.aborted).toBe(true)
    await vi.advanceTimersByTimeAsync(300)
    expect(pending).toHaveLength(2)

    pending[1]?.([{ description: 'Current result', placeId: 'current' }])
    pending[0]?.([{ description: 'Stale result', placeId: 'stale' }])
    await flushPromises()

    expect(wrapper.text()).toContain('Current result')
    expect(wrapper.text()).not.toContain('Stale result')
  })

  it('aborts the in-flight autocomplete request when unmounted', async () => {
    vi.useFakeTimers()
    let requestSignal: AbortSignal | undefined
    vi.spyOn(geocoding, 'autocompleteAddress').mockImplementation((_query, signal) => {
      requestSignal = signal
      return new Promise(() => undefined)
    })
    const wrapper = mount(AddressSearchBox, {
      global: { plugins: [makeI18n()] }, props: { modelValue: '' },
    })

    await wrapper.find('input').setValue('Address')
    await vi.advanceTimersByTimeAsync(300)
    wrapper.unmount()

    expect(requestSignal?.aborted).toBe(true)
  })
})
