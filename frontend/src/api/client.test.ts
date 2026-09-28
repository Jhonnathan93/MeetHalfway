import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiClient } from './client'
import { ApiError } from './errors'

/**
 * Unit tests for the shared {@link ApiClient} (Task 8.2). They verify the
 * preserved transport conventions with a mocked global `fetch`: the `/api/v1`
 * base URL is prefixed onto every path (Req 9.1, 9.6), request bodies are
 * JSON-stringified with the correct method/headers, responses are parsed as
 * typed objects, and non-2xx responses normalize to a typed {@link ApiError}
 * carrying the backend status and `message` (Req 9.4).
 */

/** Builds a mock `Response` with a JSON body and the given status. */
function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

/** Builds a mock `Response` with no body (for statuses that forbid one). */
function emptyResponse(status: number): Response {
  return new Response(null, { status })
}

/** Installs a mocked `fetch` returning `response` and returns the spy. */
function stubFetch(response: Response): ReturnType<typeof vi.fn> {
  const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('ApiClient', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('prefixes the /api/v1 base URL onto GET paths with a JSON Accept header', async () => {
    const fetchMock = stubFetch(jsonResponse({ ok: true }))
    const client = new ApiClient()

    await client.get('/meetings/abc')

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/meetings/abc')
    expect(url.startsWith('/api/v1')).toBe(true)
    expect((init.headers as Record<string, string>).Accept).toBe('application/json')
  })

  it('honors a custom base URL passed to the constructor', async () => {
    const fetchMock = stubFetch(jsonResponse({ ok: true }))
    const client = new ApiClient('/custom/base')

    await client.get('/thing')

    const [url] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/custom/base/thing')
  })

  it('JSON-stringifies POST bodies and sets Content-Type', async () => {
    const fetchMock = stubFetch(jsonResponse({ created: true }, 201))
    const client = new ApiClient()
    const body = { participants: [{ name: 'Ana', lat: 6.2, lng: -75.5 }] }

    await client.post('/meetings', body)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/meetings')
    expect(init.method).toBe('POST')
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json')
    expect(init.body).toBe(JSON.stringify(body))
  })

  it('omits the body and Content-Type for a POST without a payload', async () => {
    const fetchMock = stubFetch(jsonResponse({ ok: true }))
    const client = new ApiClient()

    await client.post('/meetings/abc/recommendations')

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(init.method).toBe('POST')
    expect(init.body).toBeUndefined()
    expect((init.headers as Record<string, string>)['Content-Type']).toBeUndefined()
  })

  it('JSON-stringifies PUT bodies against the prefixed URL', async () => {
    const fetchMock = stubFetch(jsonResponse({ ok: true }))
    const client = new ApiClient()
    const body = { transportMode: 'walking' }

    await client.put('/meetings/xyz', body)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/meetings/xyz')
    expect(init.method).toBe('PUT')
    expect(init.body).toBe(JSON.stringify(body))
  })

  it('issues a DELETE against the prefixed URL', async () => {
    const fetchMock = stubFetch(emptyResponse(204))
    const client = new ApiClient()

    await client.delete('/meetings/xyz')

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/meetings/xyz')
    expect(init.method).toBe('DELETE')
  })

  it('deserializes a response body into a typed object via readJson', async () => {
    stubFetch(jsonResponse({ urlCode: 'abc', transportMode: 'driving' }))
    const client = new ApiClient()

    const response = await client.get('/meetings/abc')
    const parsed = await client.readJson<{ urlCode: string; transportMode: string }>(response)

    expect(parsed).toEqual({ urlCode: 'abc', transportMode: 'driving' })
  })

  it('parseError throws an ApiError carrying the status and backend message', async () => {
    stubFetch(jsonResponse({ message: 'Meeting is invalid' }, 400))
    const client = new ApiClient()

    const response = await client.get('/meetings/bad')

    await expect(client.parseError(response)).rejects.toMatchObject({
      name: 'ApiError',
      status: 400,
      message: 'Meeting is invalid',
    })
  })

  it('parseError falls back to a status message when the body has no message field', async () => {
    stubFetch(new Response('not json', { status: 503 }))
    const client = new ApiClient()

    const response = await client.get('/meetings/bad')

    await expect(client.parseError(response)).rejects.toMatchObject({
      name: 'ApiError',
      status: 503,
      message: 'Request failed with status 503',
    })
  })

  it('exposes ApiError as a distinguishable error type', async () => {
    stubFetch(jsonResponse({ message: 'nope' }, 500))
    const client = new ApiClient()
    const response = await client.get('/x')

    await expect(client.parseError(response)).rejects.toBeInstanceOf(ApiError)
  })
})
