<script setup lang="ts">
/**
 * Meeting creation form. Collects each participant's name and location via
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
} from '@/features/meeting/types'
import { resolveAddress } from '@/features/meeting/geocodingApi'

interface ParticipantDraft {
  name: string
  address: string
  lat: number | null
  lng: number | null
}

const props = defineProps<{ submitting: boolean }>()

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
    const coordinate = await resolveAddress(suggestion.placeId)
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
    <h2 class="meeting-form__heading">{{ t('form.heading') }}</h2>

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
      {{ t('form.submit') }}
    </button>
  </form>
</template>

<style scoped>
.meeting-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  margin-top: var(--space-5);
  padding-top: var(--space-5);
  border-top: 1px solid var(--color-rule);
}

.meeting-form__heading {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 760;
  letter-spacing: -0.025em;
}

.meeting-form__mode {
  margin: 0;
  border: 0;
  border-block: 1px solid var(--color-rule);
  padding: var(--space-3) 0;
  display: flex;
  gap: var(--space-4);
  color: var(--color-text);
  font-size: .84rem;
}

.meeting-form__mode legend { padding: 0 var(--space-2) 0 0; color: var(--color-text-subtle); font-size: .68rem; font-weight: 750; letter-spacing: .1em; text-transform: uppercase; }
.meeting-form__mode label { display: inline-flex; align-items: center; gap: 6px; font-weight: 650; }
.meeting-form__mode input { accent-color: var(--color-primary); }

.meeting-form__participant {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-4) 0;
  border-bottom: 1px solid var(--color-rule);
}

.meeting-form__participants h3 { margin: 0 0 var(--space-1); color: var(--color-text); font-size: .9rem; font-weight: 750; letter-spacing: -.01em; }

.meeting-form__name {
  background-color: transparent;
  color: var(--color-text);
  border: 0;
  border-bottom: 1px solid var(--color-border-strong);
  border-radius: 0;
  padding: var(--space-2) 0;
  font: inherit;
  font-weight: 680;
}

.meeting-form__add,
.meeting-form__submit,
.meeting-form__remove {
  border: 0;
  border-radius: var(--radius-sm);
  padding: var(--space-2) var(--space-3);
  font: inherit;
  cursor: pointer;
}

.meeting-form__remove {
  background: transparent;
  color: var(--color-danger);
  justify-self: start;
  padding: var(--space-1) 0;
}

.meeting-form__submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.meeting-form__submit { min-height: 48px; background: var(--color-primary); color: #fffef9; font-weight: 750; letter-spacing: .01em; transition: background-color 160ms ease, transform 160ms ease; }
.meeting-form__add { justify-self: start; color: var(--color-primary); background: transparent; font-weight: 700; padding-left: 0; }
.meeting-form__add::before { content: '+'; margin-right: 7px; font-size: 1.1rem; font-weight: 400; }
.meeting-form__add:hover { color: var(--color-primary-hover); }
.meeting-form__submit:not(:disabled):hover { background: var(--color-primary-hover); transform: translateY(-1px); }

.meeting-form__error {
  margin: 0;
  color: var(--color-danger);
}
</style>
