import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Meeting, MeetingRequest, Recommendation, RoutingFailure } from './types'
import { computeRecommendations, createMeeting } from './meetingApi'
import { ApiError, RoutingFailureError } from '@/shared/api/errors'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function stubFetch(response: Response): ReturnType<typeof vi.fn> {
  const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const request: MeetingRequest = {
  participants: [
    { name: 'Ana', lat: 6.2, lng: -75.57 },
    { name: 'Beto', lat: 6.25, lng: -75.6 },
  ],
  transportMode: 'driving',
}

const meeting: Meeting = {
  urlCode: 'abc123',
  participants: [
    { id: 'p1', name: 'Ana', location: { lat: 6.2, lng: -75.57 } },
    { id: 'p2', name: 'Beto', location: { lat: 6.25, lng: -75.6 } },
  ],
  transportMode: 'driving',
  recommendation: null,
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('meeting API', () => {
  it('creates a meeting through the versioned API and returns its typed response', async () => {
    const fetchMock = stubFetch(jsonResponse(meeting, 201))

    await expect(createMeeting(request)).resolves.toEqual(meeting)

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/meetings', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify(request),
    }))
  })

  it('computes recommendations for an encoded meeting code', async () => {
    const recommendation: Recommendation = {
      results: {
        fastest: { point: { lat: 6.2, lng: -75.5 }, perParticipant: {}, sumTime: 0, maxTime: 0, stdDev: 0, candidateId: 'fastest', recommended: true },
        minimax: { point: { lat: 6.2, lng: -75.5 }, perParticipant: {}, sumTime: 0, maxTime: 0, stdDev: 0, candidateId: 'minimax', recommended: true },
        fairest: { point: { lat: 6.2, lng: -75.5 }, perParticipant: {}, sumTime: 0, maxTime: 0, stdDev: 0, candidateId: 'fairest', recommended: true },
      },
      outlierTradeoff: null,
      warnings: [],
    }
    const fetchMock = stubFetch(jsonResponse(recommendation))

    await expect(computeRecommendations('a b/c')).resolves.toEqual(recommendation)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/meetings/a%20b%2Fc/recommendations', expect.objectContaining({ method: 'POST' }),
    )
  })

  it('preserves the actionable routing failure returned by the backend', async () => {
    const failure: RoutingFailure = {
      code: 'ROUTING_FAILURE',
      message: 'Some participants are unroutable',
      errors: [{ participantId: 'p2', location: { lat: 6.25, lng: -75.6 }, reason: 'No route found' }],
    }
    stubFetch(jsonResponse(failure, 422))

    await expect(computeRecommendations('abc123')).rejects.toMatchObject({
      name: 'RoutingFailureError',
      failure,
      message: failure.message,
    } satisfies Partial<RoutingFailureError>)
  })

  it('normalizes other backend errors', async () => {
    stubFetch(jsonResponse({ message: 'Unavailable' }, 503))
    await expect(createMeeting(request)).rejects.toMatchObject({
      name: 'ApiError', status: 503, message: 'Unavailable',
    } satisfies Partial<ApiError>)
  })
})
