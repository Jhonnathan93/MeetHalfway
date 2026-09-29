import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages, type Locale, type MessageSchema } from '@/shared/i18n/messages'
import LanguageSelector from './LanguageSelector.vue'

function makeI18n(locale: Locale = 'es') {
  return createI18n<[MessageSchema], Locale>({ legacy: false, locale, fallbackLocale: 'en', messages })
}

describe('LanguageSelector', () => {
  it('switches between Spanish and English', async () => {
    const i18n = makeI18n()
    const wrapper = mount(LanguageSelector, { global: { plugins: [i18n] } })

    expect(wrapper.text()).toContain('Idioma')
    await wrapper.find('select').setValue('en')
    expect(wrapper.text()).toContain('Language')
  })
})
