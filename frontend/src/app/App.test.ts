import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import fc from 'fast-check'
import { createI18n } from 'vue-i18n'
import App from './App.vue'
import { messages, type Locale, type MessageSchema } from '@/shared/i18n/messages'

function makeI18n(locale: Locale = 'en') {
  return createI18n<[MessageSchema], Locale>({
    legacy: false,
    locale,
    fallbackLocale: 'en',
    messages,
  })
}

function mountApp(locale: Locale = 'en') {
  return mount(App, { global: { plugins: [makeI18n(locale)] } })
}

describe('App shell', () => {
  it('renders the application title (Vue Test Utils smoke test)', () => {
    const wrapper = mountApp()
    expect(wrapper.find('.app-navbar__brand').text()).toContain('MeetHalfway')
  })

  it('renders the map-first workspace regions', () => {
    const wrapper = mountApp()
    expect(wrapper.find('.meeting-workspace').exists()).toBe(true)
    expect(wrapper.find('.meeting-map-area').exists()).toBe(true)
    expect(wrapper.find('.meeting-sidebar').exists()).toBe(true)
  })

  it('mounts the meeting form and language selector', () => {
    const wrapper = mountApp()
    expect(wrapper.find('.meeting-form').exists()).toBe(true)
    expect(wrapper.find('.language-selector').exists()).toBe(true)
  })
})

describe('toolchain smoke test (fast-check)', () => {
  it('exercises fast-check with a trivial property', () => {
    fc.assert(
      fc.property(fc.integer(), (n) => n + 0 === n),
      { numRuns: 100 },
    )
  })
})
