import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Meeting, MeetingRequest, Recommendation, RoutingFailure } from '@/types/Meeting'
import {
  createMeeting,
  getMeeting,
  editMeeting,
  deleteMeeting,
  computeRecommendations,
} from './meetings'
import { ApiError, RoutingFailureError } from './errors'

/**
 * Unit tests for the meetings domain client (Task 8.2). Each test mocks the
 * global `fetch` and asserts both the preserved status conventions (getMeeting
 * 404 → null, recommendations 422 → RoutingFailureError, other non-2xx →
 * ApiError) and that the request targets the correct `/api/v1` URL with the
 * expected method/body and round-trips typed payloads (Req 9.1, 9.4, 9.6).
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

const sampleRequest: MeetingRequest = {
  participants: [
    { name: 'Ana', lat: 6.2, lng: -75.57 },
    { name: 'Beto', lat: 6.25, lng: -75.6 },
  ],
  transportMode: 'driving',
}

const sampleMeeting: Meeting = {
  urlCode: 'abc123',
  participants: [
    { id: 'p1', name: 'Ana', location: { lat: 6.2, lng: -75.57 } },
    { id: 'p2', name: 'Beto', location: { lat: 6.25, lng: -75.6 } },
  ],
  transportMode: 'driving',
  recommendation: null,
}

describe('meetings client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('createMeeting POSTs the JSON body to /api/v1/meetings and returns the typed meeting', async () => {
    const fetchMock = stubFetch(jsonResponse(sampleMeeting, 201))

    const result = await createMeeting(sampleRequest)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/meetings')
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify(sampleRequest))
    expect(result).toEqual(sampleMeeting)
  })

  it('getMeeting GETs the encoded code URL and returns the typed meeting on 200', async () => {
    const fetchMock = stubFetch(jsonResponse(sampleMeeting))

    const result = await getMeeting('abc123')

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/meetings/abc123')
    expect(init.method ?? 'GET').toBe('GET')
    expect(result).toEqual(sampleMeeting)
  })

  it('getMeeting returns null on 404', async () => {
    stubFetch(emptyResponse(404))

    await expect(getMeeting('missing')).resolves.toBeNull()
  })

  it('getMeeting throws an ApiError with backend message on other non-2xx', async () => {
    stubFetch(jsonResponse({ message: 'Server exploded' }, 500))

    await expect(getMeeting('abc123')).rejects.toMatchObject({
      name: 'ApiError',
      status: 500,
      message: 'Server exploded',
    })
  })

  it('getMeeting percent-encodes the URL code', async () => {
    const fetchMock = stubFetch(jsonResponse(sampleMeeting))

    await getMeeting('a b/c')

    const [url] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/meetings/a%20b%2Fc')
  })

  it('editMeeting PUTs the JSON body to the encoded code URL and returns the meeting', async () => {
    const updated: Meeting = { ...sampleMeeting, transportMode: 'walking' }
    const fetchMock = stubFetch(jsonResponse(updated))

    const result = await editMeeting('abc123', { ...sampleRequest, transportMode: 'walking' })

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/meetings/abc123')
    expect(init.method).toBe('PUT')
    expect(init.body).toBe(JSON.stringify({ ...sampleRequest, transportMode: 'walking' }))
    expect(result).toEqual(updated)
  })

  it('deleteMeeting DELETEs the encoded code URL and resolves on 204', async () => {
    const fetchMock = stubFetch(emptyResponse(204))

    await expect(deleteMeeting('abc123')).resolves.toBeUndefined()

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/meetings/abc123')
    expect(init.method).toBe('DELETE')
  })

  it('deleteMeeting tolerates a 404 without throwing', async () => {
    stubFetch(emptyResponse(404))

    await expect(deleteMeeting('missing')).resolves.toBeUndefined()
  })

  it('deleteMeeting throws an ApiError on a non-404 failure', async () => {
    stubFetch(jsonResponse({ message: 'Conflict' }, 409))

    await expect(deleteMeeting('abc123')).rejects.toBeInstanceOf(ApiError)
  })

  it('computeRecommendations POSTs to the recommendations URL and returns the typed result', async () => {
    const recommendation: Recommendation = {
      results: {
        fastest: {
          point: { lat: 6.22, lng: -75.58 },
          perParticipant: { p1: 10, p2: 12 },
          sumTime: 22,
          maxTime: 12,
          stdDev: 1,
          candidateId: 'fastest',
          recommended: true,
        },
        minimax: {
          point: { lat: 6.23, lng: -75.59 },
          perParticipant: { p1: 11, p2: 11 },
          sumTime: 22,
          maxTime: 11,
          stdDev: 0,
          candidateId: 'minimax',
          recommended: true,
        },
        fairest: {
          point: { lat: 6.24, lng: -75.585 },
          perParticipant: { p1: 11, p2: 12 },
          sumTime: 23,
          maxTime: 12,
          stdDev: 0.5,
          candidateId: 'fairest',
          recommended: true,
        },
      },
      outlierTradeoff: null,
      warnings: [],
    }
    const fetchMock = stubFetch(jsonResponse(recommendation))

    const result = await computeRecommendations('abc123')

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/meetings/abc123/recommendations')
    expect(init.method).toBe('POST')
    expect(result).toEqual(recommendation)
  })

  it('computeRecommendations throws RoutingFailureError carrying the 422 failure body', async () => {
    const failure: RoutingFailure = {
      code: 'ROUTING_FAILED',
      message: 'Some participants are unroutable',
      errors: [
        {
          participantId: 'p2',
          location: { lat: 6.25, lng: -75.6 },
          reason: 'No route found',
        },
      ],
    }
    stubFetch(jsonResponse(failure, 422))

    await expect(computeRecommendations('abc123')).rejects.toSatisfy((error: unknown) => {
      expect(error).toBeInstanceOf(RoutingFailureError)
      const routingError = error as RoutingFailureError
      expect(routingError.failure).toEqual(failure)
      expect(routingError.message).toBe(failure.message)
      return true
    })
  })

  it('computeRecommendations throws an ApiError on a non-422 failure', async () => {
    stubFetch(jsonResponse({ message: 'Bad request' }, 400))

    await expect(computeRecommendations('abc123')).rejects.toMatchObject({
      name: 'ApiError',
      status: 400,
      message: 'Bad request',
    })
  })
})
