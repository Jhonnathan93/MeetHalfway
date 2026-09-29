<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import LanguageSelector from '@/shared/components/LanguageSelector.vue'
import MeetingView from '@/features/meeting/MeetingView.vue'

const { t } = useI18n()
</script>

<template>
  <div class="app-container">
    <header class="app-header">
      <div>
        <h1 class="app-title">{{ t('app.title') }}</h1>
        <p class="app-tagline">{{ t('app.tagline') }}</p>
      </div>
      <LanguageSelector />
    </header>
    <MeetingView />
  </div>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: var(--space-3);
  margin-bottom: var(--space-4);
  border-bottom: 1px solid var(--color-border);
}

.app-title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
}

.app-tagline {
  margin: var(--space-1) 0 0;
  color: var(--color-text-muted);
  font-size: 0.875rem;
  max-width: 48ch;
}
</style>
