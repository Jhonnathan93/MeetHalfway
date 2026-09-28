/**
 * Geocoding domain client (Requirement 9.2). Wraps the `/geocode` endpoints and
 * is typed against the contracts in `@/types/Meeting` (Req 9.3), built on the
 * shared {@link ApiClient} (Req 9.1). Status conventions are preserved exactly
 * from the original flat client (resolve 422 → null).
 */
import type { AddressSuggestion, Coordinate } from '@/types/Meeting'
import { apiClient, type ApiClient } from './client'

/** Fetches address autocomplete suggestions (GET /geocode/autocomplete?q=). */
export async function autocompleteAddress(query: string): Promise<AddressSuggestion[]> {
  const trimmed = query.trim()
  if (trimmed.length === 0) {
    return []
  }
  const client: ApiClient = apiClient
  const response = await client.get(`/geocode/autocomplete?q=${encodeURIComponent(trimmed)}`)
  if (!response.ok) {
    return client.parseError(response)
  }
  return client.readJson<AddressSuggestion[]>(response)
}

/**
 * Resolves a chosen address (or suggestion label) to a precise coordinate
 * (GET /geocode/resolve?q=). Returns null when the backend responds 422
 * "not found", so the caller can prompt the user to correct the address rather
 * than proceeding with a wrong or fabricated location.
 */
export async function resolveAddress(query: string): Promise<Coordinate | null> {
  const trimmed = query.trim()
  if (trimmed.length === 0) {
    return null
  }
  const client: ApiClient = apiClient
  const response = await client.get(`/geocode/resolve?q=${encodeURIComponent(trimmed)}`)
  if (response.status === 422) {
    return null
  }
  if (!response.ok) {
    return client.parseError(response)
  }
  return client.readJson<Coordinate>(response)
}
