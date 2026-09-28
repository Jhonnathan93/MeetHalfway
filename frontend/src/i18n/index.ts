/**
 * i18n plugin instance (vue-i18n, Composition API mode). ES is the default
 * locale; EN is fully translated. The `MessageSchema` type ties both catalogs
 * to the same key shape for type-safe `t()` usage.
 */
import { createI18n } from 'vue-i18n'
import { DEFAULT_LOCALE, messages, type Locale, type MessageSchema } from './messages'

export const i18n = createI18n<[MessageSchema], Locale>({
  legacy: false,
  locale: DEFAULT_LOCALE,
  fallbackLocale: 'en',
  messages,
})

export { DEFAULT_LOCALE, SUPPORTED_LOCALES } from './messages'
export type { Locale } from './messages'
