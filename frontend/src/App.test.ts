import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import fc from 'fast-check'
import App from './App.vue'

describe('App shell', () => {
  it('renders the application title (Vue Test Utils smoke test)', () => {
    const wrapper = mount(App)
    expect(wrapper.find('.app-title').text()).toBe('MeetHalfway')
  })

  it('renders the desktop-first layout regions', () => {
    const wrapper = mount(App)
    expect(wrapper.find('.app-main').exists()).toBe(true)
    expect(wrapper.find('.app-content').exists()).toBe(true)
    expect(wrapper.find('.app-sidebar').exists()).toBe(true)
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
