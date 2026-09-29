import type { Meeting, MeetingRequest, Recommendation, RoutingFailure } from './types'
import { apiError, apiRequest, jsonBody, requestJson } from '@/shared/api/http'
import { RoutingFailureError } from '@/shared/api/errors'

/** Creates a meeting and returns the backend's typed representation. */
export function createMeeting(request: MeetingRequest): Promise<Meeting> {
  return requestJson<Meeting>('/meetings', jsonBody('POST', request))
}

/** Computes all three recommendation strategies for an existing meeting. */
export async function computeRecommendations(code: string): Promise<Recommendation> {
  const response = await apiRequest(
    `/meetings/${encodeURIComponent(code)}/recommendations`,
    { method: 'POST' },
  )
  if (response.status === 422) {
    throw new RoutingFailureError(await response.json() as RoutingFailure)
  }
  if (!response.ok) {
    throw await apiError(response)
  }
  return response.json() as Promise<Recommendation>
}
