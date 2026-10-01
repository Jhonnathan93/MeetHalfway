<script setup lang="ts">
/**
 * Per-participant address entry backed by the backend geocoding autocomplete
 * endpoint (Requirement 9.7). Text + suggestion list only — there is NO
 * map-click selection anywhere in the MVP (Requirement 9.8). Emits the chosen
 * suggestion's description upward; the parent tracks the resolved location.
 */
import { getCurrentInstance, onBeforeUnmount, ref, watch } from 'vue'
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
const activeIndex = ref(-1)
const listId = `address-options-${getCurrentInstance()?.uid ?? 'input'}`

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
    activeIndex.value = -1
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
    if (version === searchVersion) {
      suggestions.value = results
      activeIndex.value = -1
    }
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
  activeIndex.value = -1
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    if (suggestions.value.length > 0) {
      event.preventDefault()
      suggestions.value = []
      activeIndex.value = -1
    }
    return
  }

  if (suggestions.value.length === 0) return

  if (event.key === 'ArrowDown') {
    event.preventDefault()
    activeIndex.value = Math.min(activeIndex.value + 1, suggestions.value.length - 1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    activeIndex.value = Math.max(activeIndex.value - 1, 0)
  } else if (event.key === 'Enter' && activeIndex.value >= 0) {
    event.preventDefault()
    choose(suggestions.value[activeIndex.value])
  }
}

// Keep the input in sync when the parent resets the value.
watch(
  () => props.modelValue,
  (value) => {
    if (value.length === 0) {
      cancelPendingSearch()
      suggestions.value = []
      hasQueried.value = false
      activeIndex.value = -1
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
      role="combobox"
      :aria-expanded="suggestions.length > 0"
      :aria-controls="listId"
      :aria-activedescendant="activeIndex >= 0 ? `${listId}-${activeIndex}` : ''"
      autocomplete="off"
      @input="onInput"
      @keydown="onKeydown"
    />

    <p v-if="isSearching" class="address-search__status">{{ t('search.searching') }}</p>

    <ul v-else-if="suggestions.length > 0" :id="listId" class="address-search__list" role="listbox">
      <li v-for="(item, index) in suggestions" :key="item.placeId" class="address-search__item" role="presentation">
        <button :id="`${listId}-${index}`" type="button" class="address-search__option" role="option" :aria-selected="activeIndex === index" @click="choose(item)">
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
  background-color: transparent;
  color: var(--color-text);
  border: 0;
  border-bottom: 1px solid var(--color-border);
  border-radius: 0;
  padding: var(--space-2) 0;
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
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-xs);
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
  background-color: var(--color-primary-soft);
}

.address-search__option[aria-selected='true'] { background-color: var(--color-primary-soft); }
</style>
