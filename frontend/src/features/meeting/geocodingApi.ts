import type { AddressSuggestion, Coordinate } from './types'
import { apiError, apiRequest, requestJson } from '@/shared/api/http'

/** Gets address suggestions from the backend, optionally cancelling stale searches. */
export function autocompleteAddress(
  query: string,
  signal?: AbortSignal,
): Promise<AddressSuggestion[]> {
  const trimmed = query.trim()
  if (!trimmed) return Promise.resolve([])
  const options = signal ? { signal } : undefined
  return requestJson<AddressSuggestion[]>(`/geocode/autocomplete?q=${encodeURIComponent(trimmed)}`, options)
}

/** Resolves an address to coordinates; a 422 response means no match was found. */
export async function resolveAddress(query: string): Promise<Coordinate | null> {
  const trimmed = query.trim()
  if (!trimmed) return null

  const response = await apiRequest(`/geocode/resolve?q=${encodeURIComponent(trimmed)}`)
  if (response.status === 422) return null
  if (!response.ok) throw await apiError(response)
  return response.json() as Promise<Coordinate>
}
