import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest, jsonBody, requestJson } from './http'
import { ApiError } from './errors'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('shared HTTP helpers', () => {
  it('prefixes API paths and sets JSON accept without dropping caller headers', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response('{}'))
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest('/meetings', { method: 'POST', headers: { 'Content-Type': 'application/json' } })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = new Headers(init.headers)
    expect(url).toBe('/api/v1/meetings')
    expect(init.method).toBe('POST')
    expect(headers.get('Accept')).toBe('application/json')
    expect(headers.get('Content-Type')).toBe('application/json')
  })

  it('creates the small JSON request options used by feature API modules', () => {
    expect(jsonBody('POST', { name: 'Ana' })).toEqual({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{"name":"Ana"}',
    })
  })

  it('returns typed JSON for successful requests', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(
      new Response('{"ok":true}', { headers: { 'Content-Type': 'application/json' } }),
    ))

    await expect(requestJson<{ ok: boolean }>('/health')).resolves.toEqual({ ok: true })
  })

  it('uses the backend error message and falls back to the status when not JSON', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(
      new Response('{"message":"Not available"}', { status: 503 }),
    ))
    await expect(requestJson('/health')).rejects.toMatchObject({
      name: 'ApiError', status: 503, message: 'Not available',
    } satisfies Partial<ApiError>)

    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(new Response('oops', { status: 502 })))
    await expect(requestJson('/health')).rejects.toMatchObject({
      status: 502, message: 'Request failed with status 502',
    })
  })
})
