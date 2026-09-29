<script setup lang="ts">
/**
 * Per-participant address entry backed by the backend geocoding autocomplete
 * endpoint (Requirement 9.7). Text + suggestion list only — there is NO
 * map-click selection anywhere in the MVP (Requirement 9.8). Emits the chosen
 * suggestion's description upward; the parent tracks the resolved location.
 */
import { onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { autocompleteAddress } from '@/features/meeting/geocodingApi'
import type { AddressSuggestion } from '@/features/meeting/types'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
  (event: 'select', suggestion: AddressSuggestion): void
}>()

const { t } = useI18n()

const suggestions = ref<AddressSuggestion[]>([])
const isSearching = ref(false)
const hasQueried = ref(false)

const DEBOUNCE_MS = 300
let debounceTimer: ReturnType<typeof setTimeout> | undefined
let activeRequest: AbortController | undefined
let searchVersion = 0

function onInput(event: Event): void {
  const value = (event.target as HTMLInputElement).value
  emit('update:modelValue', value)
  scheduleSearch(value)
}

function scheduleSearch(query: string): void {
  cancelPendingSearch()
  const version = searchVersion
  if (query.trim().length === 0) {
    suggestions.value = []
    hasQueried.value = false
    return
  }
  debounceTimer = setTimeout(() => void runSearch(query, version), DEBOUNCE_MS)
}

function cancelPendingSearch(): void {
  if (debounceTimer !== undefined) clearTimeout(debounceTimer)
  debounceTimer = undefined
  activeRequest?.abort()
  activeRequest = undefined
  searchVersion += 1
  isSearching.value = false
}

async function runSearch(query: string, version: number): Promise<void> {
  const trimmed = query.trim()
  if (trimmed.length === 0) {
    suggestions.value = []
    hasQueried.value = false
    return
  }
  const controller = new AbortController()
  activeRequest = controller
  isSearching.value = true
  try {
    const results = await autocompleteAddress(trimmed, controller.signal)
    if (version === searchVersion) suggestions.value = results
  } catch {
    if (version === searchVersion && !controller.signal.aborted) suggestions.value = []
  } finally {
    if (version === searchVersion) {
      activeRequest = undefined
      isSearching.value = false
      hasQueried.value = true
    }
  }
}

function choose(suggestion: AddressSuggestion): void {
  cancelPendingSearch()
  emit('update:modelValue', suggestion.description)
  emit('select', suggestion)
  suggestions.value = []
  hasQueried.value = false
}

// Keep the input in sync when the parent resets the value.
watch(
  () => props.modelValue,
  (value) => {
    if (value.length === 0) {
      cancelPendingSearch()
      suggestions.value = []
      hasQueried.value = false
    }
  },
)

onBeforeUnmount(cancelPendingSearch)
</script>

<template>
  <div class="address-search">
    <input
      type="text"
      class="address-search__input"
      :value="modelValue"
      :placeholder="t('search.placeholder')"
      :aria-label="t('form.participantAddress')"
      autocomplete="off"
      @input="onInput"
    />

    <p v-if="isSearching" class="address-search__status">{{ t('search.searching') }}</p>

    <ul v-else-if="suggestions.length > 0" class="address-search__list" role="listbox">
      <li v-for="item in suggestions" :key="item.placeId" class="address-search__item">
        <button type="button" class="address-search__option" @click="choose(item)">
          {{ item.description }}
        </button>
      </li>
    </ul>

    <p
      v-else-if="hasQueried && modelValue.trim().length > 0"
      class="address-search__status"
    >
      {{ t('search.noResults') }}
    </p>
  </div>
</template>

<style scoped>
.address-search {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.address-search__input {
  background-color: var(--color-surface-raised);
  color: var(--color-text);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-2);
  font: inherit;
  width: 100%;
}

.address-search__status {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 0.8125rem;
}

.address-search__list {
  list-style: none;
  margin: 0;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background-color: var(--color-surface-raised);
  overflow: hidden;
}

.address-search__option {
  display: block;
  width: 100%;
  text-align: left;
  background: none;
  border: none;
  color: var(--color-text);
  padding: var(--space-2);
  font: inherit;
  cursor: pointer;
}

.address-search__option:hover {
  background-color: var(--color-surface);
}
</style>
