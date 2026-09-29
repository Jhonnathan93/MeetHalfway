<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Participant } from '@/features/meeting/types'

const props = defineProps<{
  participants: Participant[]
  selectedParticipantId: string | null
}>()

const emit = defineEmits<{ select: [participantId: string] }>()
const { t } = useI18n()

const participantItems = computed(() => props.participants.map((participant) => ({
  ...participant,
  initials: participant.name.trim().slice(0, 2).toUpperCase() || participant.id.slice(0, 2).toUpperCase(),
})))
</script>

<template>
  <section class="participant-list" :aria-label="t('participants.heading')">
    <div class="participant-list__heading">
      <h2>{{ t('participants.heading') }}</h2>
      <span v-if="participants.length" class="participant-list__count">{{ participants.length }}</span>
    </div>
    <p v-if="participants.length === 0" class="participant-list__empty">{{ t('participants.empty') }}</p>
    <ul v-else>
      <li v-for="participant in participantItems" :key="participant.id">
        <button
          type="button"
          class="participant-list__item"
          :class="{ 'participant-list__item--selected': selectedParticipantId === participant.id }"
          :aria-pressed="selectedParticipantId === participant.id"
          @click="emit('select', participant.id)"
        >
          <span class="participant-list__avatar" aria-hidden="true">{{ participant.initials }}</span>
          <span class="participant-list__name">{{ participant.name || participant.id }}</span>
          <span v-if="selectedParticipantId === participant.id" class="participant-list__selected">
            {{ t('participants.selected') }}
          </span>
        </button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.participant-list { border-top: 1px solid var(--color-border); padding-top: var(--space-4); }
.participant-list__heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-2); }
.participant-list h2 { margin: 0; font-family: var(--font-display); font-size: 1.45rem; font-weight: 400; letter-spacing: .03em; }
.participant-list__count { min-width: 22px; padding: 2px 7px; border-radius: 99px; background: var(--color-surface-hover); color: var(--color-text-muted); font-size: .75rem; text-align: center; }
.participant-list__empty { margin: 0; color: var(--color-text-muted); font-size: .82rem; }
.participant-list ul { display: grid; gap: 3px; margin: 0; padding: 0; list-style: none; }
.participant-list__item { width: 100%; display: grid; grid-template-columns: 30px minmax(0, 1fr); align-items: center; gap: var(--space-2); padding: var(--space-2); border: 1px solid transparent; border-radius: var(--radius-sm); background: transparent; color: var(--color-text); cursor: pointer; text-align: left; }
.participant-list__item:hover { background: var(--color-surface-hover); }
.participant-list__item--selected { border-color: var(--color-primary); background: var(--color-primary-soft); }
.participant-list__avatar { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 50%; color: #111827; background: #d9e3ff; font-size: .66rem; font-weight: 800; }
.participant-list__name { overflow: hidden; font-size: .82rem; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.participant-list__selected { grid-column: 2; color: var(--color-primary-hover); font-size: .66rem; }
</style>
