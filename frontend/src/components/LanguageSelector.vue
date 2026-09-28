<script setup lang="ts">
/**
 * Language selector: switches the interface language between Spanish and
 * English (Requirement 10.3). All labels are themselves localized.
 */
import { useI18n } from 'vue-i18n'
import { SUPPORTED_LOCALES, type Locale } from '@/i18n'

const { t, locale } = useI18n()

function onChange(event: Event): void {
  const value = (event.target as HTMLSelectElement).value as Locale
  locale.value = value
}
</script>

<template>
  <label class="language-selector">
    <span class="language-selector__label">{{ t('language.label') }}</span>
    <select
      class="language-selector__select"
      :value="locale"
      :aria-label="t('language.label')"
      @change="onChange"
    >
      <option v-for="loc in SUPPORTED_LOCALES" :key="loc" :value="loc">
        {{ t(`language.${loc}`) }}
      </option>
    </select>
  </label>
</template>

<style scoped>
.language-selector {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.language-selector__label {
  color: var(--color-text-muted);
  font-size: 0.875rem;
}

.language-selector__select {
  background-color: var(--color-surface-raised);
  color: var(--color-text);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-1) var(--space-2);
  font: inherit;
}
</style>
