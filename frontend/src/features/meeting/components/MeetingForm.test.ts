import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages, type Locale, type MessageSchema } from '@/shared/i18n/messages'
import type { Coordinate, MeetingRequest } from '../types'
import * as geocoding from '../geocodingApi'
import AddressSearchBox from './AddressSearchBox.vue'
import MeetingForm from './MeetingForm.vue'

function makeI18n(locale: Locale = 'en') {
  return createI18n<[MessageSchema], Locale>({ legacy: false, locale, fallbackLocale: 'en', messages })
}

afterEach(() => vi.restoreAllMocks())

describe('MeetingForm', () => {
  it('resolves selected addresses and submits the coordinates', async () => {
    const coordinate: Coordinate = { lat: 6.21, lng: -75.57 }
    vi.spyOn(geocoding, 'resolveAddress').mockResolvedValue(coordinate)
    const wrapper = mount(MeetingForm, {
      global: { plugins: [makeI18n()] }, props: { submitting: false },
    })

    const boxes = wrapper.findAllComponents(AddressSearchBox)
    for (const box of boxes) {
      box.vm.$emit('select', { description: 'El Poblado', placeId: 'place-1' })
    }
    await flushPromises()
    await wrapper.find('form').trigger('submit')

    expect(geocoding.resolveAddress).toHaveBeenCalledWith('place-1')

    const emitted = wrapper.emitted('submit')
    expect(emitted).toBeTruthy()
    const request = emitted?.[0]?.[0] as MeetingRequest
    expect(request.participants).toHaveLength(2)
    expect(request.participants.every((person) => person.lat === 6.21 && person.lng === -75.57)).toBe(true)
    expect(request.transportMode).toBe('driving')
  })

  it('does not submit or invent coordinates when resolution fails', async () => {
    vi.spyOn(geocoding, 'resolveAddress').mockResolvedValue(null)
    const wrapper = mount(MeetingForm, {
      global: { plugins: [makeI18n()] }, props: { submitting: false },
    })
    wrapper.findComponent(AddressSearchBox).vm.$emit('select', {
      description: 'Unknown address', placeId: 'unknown',
    })

    await flushPromises()
    await wrapper.find('form').trigger('submit')
    expect(wrapper.emitted('submit')).toBeFalsy()
  })
})
