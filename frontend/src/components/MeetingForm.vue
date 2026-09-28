<script setup lang="ts">
/**
 * Create/edit meeting form. Collects each participant's name and location via
 * address autocomplete (no map-click) and a single shared transport mode.
 * Enforces the 2–10 participant bound and mode selection in the UI for fast
 * feedback; the backend remains the authority (Requirement 1).
 */
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AddressSearchBox from './AddressSearchBox.vue'
import {
  MAX_PARTICIPANTS,
  MIN_PARTICIPANTS,
  type AddressSuggestion,
  type MeetingRequest,
  type ParticipantInput,
  type TransportMode,
} from '@/types/Meeting'
import { resolveAddress } from '@/api/meetingApi'

interface ParticipantDraft {
  name: string
  address: string
  lat: number | null
  lng: number | null
}

const props = defineProps<{
  mode: 'create' | 'edit'
  submitting: boolean
}>()

const emit = defineEmits<{
  (event: 'submit', request: MeetingRequest): void
}>()

const { t } = useI18n()

function emptyDraft(): ParticipantDraft {
  return { name: '', address: '', lat: null, lng: null }
}

const participants = reactive<ParticipantDraft[]>([emptyDraft(), emptyDraft()])
const transportMode = ref<TransportMode>('driving')
const validationMessage = ref<string>('')

const canAdd = computed(() => participants.length < MAX_PARTICIPANTS)
const canRemove = computed(() => participants.length > MIN_PARTICIPANTS)

function addParticipant(): void {
  if (canAdd.value) {
    participants.push(emptyDraft())
  }
}

function removeParticipant(index: number): void {
  if (canRemove.value) {
    participants.splice(index, 1)
  }
}

async function onSelect(index: number, suggestion: AddressSuggestion): Promise<void> {
  const draft = participants[index]
  draft.address = suggestion.description
  // Resolve the chosen address to a precise coordinate through the backend
  // (GET /geocode/resolve). We never fabricate a location: if resolution fails
  // the coordinates stay unset and validation prompts the user to correct it.
  draft.lat = null
  draft.lng = null
  try {
    const coordinate = await resolveAddress(suggestion.description)
    if (coordinate !== null) {
      draft.lat = coordinate.lat
      draft.lng = coordinate.lng
    } else {
      validationMessage.value = t('form.missingLocation')
    }
  } catch {
    validationMessage.value = t('form.missingLocation')
  }
}

function validate(): MeetingRequest | null {
  validationMessage.value = ''
  if (participants.length < MIN_PARTICIPANTS) {
    validationMessage.value = t('form.minParticipants', { min: MIN_PARTICIPANTS })
    return null
  }
  if (participants.length > MAX_PARTICIPANTS) {
    validationMessage.value = t('form.maxParticipants', { max: MAX_PARTICIPANTS })
    return null
  }
  const inputs: ParticipantInput[] = []
  for (const draft of participants) {
    if (draft.lat === null || draft.lng === null || draft.address.trim().length === 0) {
      validationMessage.value = t('form.missingLocation')
      return null
    }
    inputs.push({ name: draft.name.trim(), lat: draft.lat, lng: draft.lng })
  }
  return { participants: inputs, transportMode: transportMode.value }
}

function onSubmit(): void {
  const request = validate()
  if (request !== null) {
    emit('submit', request)
  }
}
</script>

<template>
  <form class="meeting-form" @submit.prevent="onSubmit">
    <h2 class="meeting-form__heading">
      {{ props.mode === 'edit' ? t('form.editHeading') : t('form.heading') }}
    </h2>

    <fieldset class="meeting-form__mode">
      <legend>{{ t('form.transportMode') }}</legend>
      <label>
        <input v-model="transportMode" type="radio" value="driving" />
        {{ t('form.driving') }}
      </label>
      <label>
        <input v-model="transportMode" type="radio" value="walking" />
        {{ t('form.walking') }}
      </label>
    </fieldset>

    <div class="meeting-form__participants">
      <h3>{{ t('form.participants') }}</h3>
      <div
        v-for="(participant, index) in participants"
        :key="index"
        class="meeting-form__participant"
      >
        <input
          v-model="participant.name"
          type="text"
          class="meeting-form__name"
          :placeholder="t('form.participantName')"
          :aria-label="t('form.participantName')"
        />
        <AddressSearchBox
          v-model="participant.address"
          @select="(suggestion) => onSelect(index, suggestion)"
        />
        <button
          v-if="canRemove"
          type="button"
          class="meeting-form__remove"
          @click="removeParticipant(index)"
        >
          {{ t('form.removeParticipant') }}
        </button>
      </div>

      <button
        v-if="canAdd"
        type="button"
        class="meeting-form__add"
        @click="addParticipant"
      >
        {{ t('form.addParticipant') }}
      </button>
    </div>

    <p v-if="validationMessage" class="meeting-form__error" role="alert">
      {{ validationMessage }}
    </p>

    <button type="submit" class="meeting-form__submit" :disabled="props.submitting">
      {{ props.mode === 'edit' ? t('form.submitEdit') : t('form.submit') }}
    </button>
  </form>
</template>

<style scoped>
.meeting-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.meeting-form__heading {
  margin: 0;
  font-size: 1.25rem;
}

.meeting-form__mode {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-2) var(--space-3);
  display: flex;
  gap: var(--space-4);
}

.meeting-form__participant {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  margin-bottom: var(--space-2);
}

.meeting-form__name {
  background-color: var(--color-surface-raised);
  color: var(--color-text);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-2);
  font: inherit;
}

.meeting-form__add,
.meeting-form__submit,
.meeting-form__remove {
  background-color: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-sm);
  padding: var(--space-2) var(--space-3);
  font: inherit;
  cursor: pointer;
}

.meeting-form__remove {
  background-color: transparent;
  color: var(--color-danger);
  justify-self: start;
  padding: var(--space-1) 0;
}

.meeting-form__submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.meeting-form__error {
  margin: 0;
  color: var(--color-danger);
}
</style>
