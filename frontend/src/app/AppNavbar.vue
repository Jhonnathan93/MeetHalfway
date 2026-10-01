<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import LanguageSelector from '@/shared/components/LanguageSelector.vue'

const { t } = useI18n()
</script>

<template>
  <header class="app-navbar">
    <a class="app-navbar__brand" href="#workspace" :aria-label="t('app.title')">
      <span class="app-navbar__mark" aria-hidden="true">
        <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M7 20h25M25 13l7 7-7 7M15 27l-7-7 7-7" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
      </span>
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
  min-height: 64px;
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: 0 var(--space-5);
  color: #eef3ed;
  border-bottom: 1px solid #294055;
  background: #12243a;
}

.app-navbar__brand {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
  color: #fffef9;
  text-decoration: none;
  min-width: max-content;
}

.app-navbar__mark {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid #86a394;
  border-radius: 50%;
  color: #d1e6d9;
}

.app-navbar__mark svg { width: 21px; height: 21px; }

.app-navbar__brand strong {
  display: block;
  font-size: 1.02rem;
  font-weight: 750;
  letter-spacing: -0.02em;
  line-height: 1;
}

.app-navbar__brand small {
  display: block;
  margin-top: 2px;
  color: #aabdb0;
  font-size: 0.67rem;
  font-weight: 600;
  letter-spacing: 0.1em;
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
  color: #b9c8be;
  background: transparent;
  font-size: 0.85rem;
  font-weight: 600;
  text-decoration: none;
}

.app-navbar__link--active {
  color: #fffef9;
  border-bottom-color: #7ec4a7;
}

.app-navbar__link:not(:disabled):hover { color: #fffef9; }

.app-navbar__link:disabled { opacity: 0.42; }
.app-navbar :deep(.language-selector) { margin-left: auto; }

@media (max-width: 680px) {
  .app-navbar { min-height: 60px; padding: 0 var(--space-3); gap: var(--space-3); }
  .app-navbar__brand small, .app-navbar__nav { display: none; }
}
</style>
