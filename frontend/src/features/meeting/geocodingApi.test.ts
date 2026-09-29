import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AddressSuggestion, Coordinate } from '@/features/meeting/types'
import { autocompleteAddress, resolveAddress } from './geocodingApi'

/**
 * Unit tests for the geocoding domain client (Task 8.2). They mock the global
 * `fetch` and assert the preserved conventions: an empty query short-circuits
 * without a request (autocomplete → [], resolve → null), resolve 422 → null,
 * other non-2xx → ApiError, correct `/api/v1/geocode` URLs, and typed
 * (de)serialization (Req 9.1, 9.4, 9.6).
 */

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function emptyResponse(status: number): Response {
  return new Response(null, { status })
}

function stubFetch(response: Response): ReturnType<typeof vi.fn> {
  const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('geocoding client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('autocompleteAddress GETs the encoded query and returns typed suggestions', async () => {
    const suggestions: AddressSuggestion[] = [
      { description: 'Parque Lleras, Medellín', placeId: 'place-1' },
      { description: 'Parque del Poblado, Medellín', placeId: 'place-2' },
    ]
    const fetchMock = stubFetch(jsonResponse(suggestions))

    const result = await autocompleteAddress('Parque de')

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/geocode/autocomplete?q=Parque%20de')
    expect(init.method ?? 'GET').toBe('GET')
    expect(result).toEqual(suggestions)
  })

  it('autocompleteAddress returns [] without fetching when the query is empty', async () => {
    const fetchMock = stubFetch(jsonResponse([]))

    await expect(autocompleteAddress('   ')).resolves.toEqual([])
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('autocompleteAddress throws an ApiError on a non-2xx response', async () => {
    stubFetch(jsonResponse({ message: 'Rate limited' }, 429))

    await expect(autocompleteAddress('Parque')).rejects.toMatchObject({
      name: 'ApiError',
      status: 429,
      message: 'Rate limited',
    })
  })

  it('resolveAddress GETs the encoded query and returns the typed coordinate', async () => {
    const coordinate: Coordinate = { lat: 6.209, lng: -75.567 }
    const fetchMock = stubFetch(jsonResponse(coordinate))

    const result = await resolveAddress('Parque Lleras')

    const [url] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/geocode/resolve?q=Parque%20Lleras')
    expect(result).toEqual(coordinate)
  })

  it('resolveAddress returns null without fetching when the query is empty', async () => {
    const fetchMock = stubFetch(jsonResponse({ lat: 0, lng: 0 }))

    await expect(resolveAddress('  ')).resolves.toBeNull()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('resolveAddress returns null on a 422 not-found response', async () => {
    stubFetch(emptyResponse(422))

    await expect(resolveAddress('Nowhere at all')).resolves.toBeNull()
  })

  it('resolveAddress throws an ApiError on other non-2xx responses', async () => {
    stubFetch(jsonResponse({ message: 'Upstream failure' }, 502))

    await expect(resolveAddress('Parque Lleras')).rejects.toMatchObject({
      name: 'ApiError',
      status: 502,
      message: 'Upstream failure',
    })
  })
})
