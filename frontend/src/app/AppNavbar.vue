<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import LanguageSelector from '@/shared/components/LanguageSelector.vue'

const { t } = useI18n()
</script>

<template>
  <header class="app-navbar">
    <a class="app-navbar__brand" href="#workspace" :aria-label="t('app.title')">
      <span class="app-navbar__mark" aria-hidden="true">↔</span>
      <span>
        <strong>{{ t('app.title') }}</strong>
        <small>{{ t('app.workspace') }}</small>
      </span>
    </a>

    <nav class="app-navbar__nav" :aria-label="t('app.title')">
      <a class="app-navbar__link app-navbar__link--active" href="#workspace">{{ t('app.navMeeting') }}</a>
      <button type="button" class="app-navbar__link" disabled :title="t('app.comingSoon')">
        {{ t('app.navHistory') }}
      </button>
      <button type="button" class="app-navbar__link" disabled :title="t('app.comingSoon')">
        {{ t('app.navHelp') }}
      </button>
    </nav>

    <LanguageSelector />
  </header>
</template>

<style scoped>
.app-navbar {
  min-height: 68px;
  display: flex;
  align-items: center;
  gap: var(--space-5);
  padding: 0 var(--space-5);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
}

.app-navbar__brand {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
  color: var(--color-text);
  text-decoration: none;
  min-width: max-content;
}

.app-navbar__mark {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 11px;
  color: white;
  background: var(--color-primary);
  font-size: 1.3rem;
  font-weight: 700;
}

.app-navbar__brand strong {
  display: block;
  font-family: var(--font-display);
  font-size: 1.45rem;
  font-weight: 400;
  letter-spacing: 0.035em;
  line-height: 1;
}

.app-navbar__brand small {
  display: block;
  margin-top: 2px;
  color: var(--color-text-subtle);
  font-size: 0.67rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.app-navbar__nav {
  display: flex;
  align-self: stretch;
  align-items: stretch;
  gap: var(--space-2);
}

.app-navbar__link {
  display: inline-flex;
  align-items: center;
  border: 0;
  border-bottom: 2px solid transparent;
  padding: 0 var(--space-3);
  color: var(--color-text-muted);
  background: transparent;
  font-size: 0.85rem;
  font-weight: 600;
  text-decoration: none;
}

.app-navbar__link--active {
  color: var(--color-text);
  border-bottom-color: var(--color-primary);
}

.app-navbar__link:disabled { cursor: not-allowed; opacity: 0.45; }
.app-navbar :deep(.language-selector) { margin-left: auto; }

@media (max-width: 680px) {
  .app-navbar { min-height: 60px; padding: 0 var(--space-3); gap: var(--space-3); }
  .app-navbar__brand small, .app-navbar__nav { display: none; }
}
</style>
