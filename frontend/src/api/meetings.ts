/**
 * Meetings domain client (Requirement 9.2). Groups the meeting lifecycle
 * operations plus recommendations, which are addressed under
 * `/meetings/{code}/recommendations` and always operate on a meeting. All
 * operations are typed against the contracts in `@/types/Meeting` (Req 9.3) and
 * built on the shared {@link ApiClient} (Req 9.1). Status conventions are
 * preserved exactly from the original flat client.
 */
import type { Meeting, MeetingRequest, Recommendation, RoutingFailure } from '@/types/Meeting'
import { apiClient, type ApiClient } from './client'
import { RoutingFailureError } from './errors'

/** Creates a meeting (POST /meetings). */
export async function createMeeting(request: MeetingRequest): Promise<Meeting> {
  const client: ApiClient = apiClient
  const response = await client.post('/meetings', request)
  if (!response.ok) {
    return client.parseError(response)
  }
  return client.readJson<Meeting>(response)
}

/** Retrieves a meeting by its URL code (GET /meetings/{code}); null on 404. */
export async function getMeeting(code: string): Promise<Meeting | null> {
  const client: ApiClient = apiClient
  const response = await client.get(`/meetings/${encodeURIComponent(code)}`)
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    return client.parseError(response)
  }
  return client.readJson<Meeting>(response)
}

/** Edits a meeting (PUT /meetings/{code}). */
export async function editMeeting(code: string, request: MeetingRequest): Promise<Meeting> {
  const client: ApiClient = apiClient
  const response = await client.put(`/meetings/${encodeURIComponent(code)}`, request)
  if (!response.ok) {
    return client.parseError(response)
  }
  return client.readJson<Meeting>(response)
}

/** Deletes a meeting (DELETE /meetings/{code}). */
export async function deleteMeeting(code: string): Promise<void> {
  const client: ApiClient = apiClient
  const response = await client.delete(`/meetings/${encodeURIComponent(code)}`)
  if (!response.ok && response.status !== 404) {
    await client.parseError(response)
  }
}

/**
 * Computes recommendations (POST /meetings/{code}/recommendations). On a 422
 * routing failure, throws {@link RoutingFailureError} carrying every affected
 * participant and reason so the UI can prompt a correction — never hiding a
 * participant (Requirement 6).
 */
export async function computeRecommendations(code: string): Promise<Recommendation> {
  const client: ApiClient = apiClient
  const response = await client.post(`/meetings/${encodeURIComponent(code)}/recommendations`)
  if (response.status === 422) {
    const failure = await client.readJson<RoutingFailure>(response)
    throw new RoutingFailureError(failure)
  }
  if (!response.ok) {
    return client.parseError(response)
  }
  return client.readJson<Recommendation>(response)
}
