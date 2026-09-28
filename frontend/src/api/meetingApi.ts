/**
 * Typed client for the MeetHalfway backend. It calls ONLY `/api/v1` endpoints;
 * the browser never talks to geocoding/routing providers directly, so no
 * provider secret ever reaches the frontend bundle (Requirement 11.4). In dev,
 * Vite proxies `/api/v1` to the backend (see vite.config.ts).
 */
import type {
  AddressSuggestion,
  Coordinate,
  Meeting,
  MeetingRequest,
  Recommendation,
  RoutingFailure,
} from '@/types/Meeting'

const API_BASE = '/api/v1'

/** Thrown when the backend returns 422 with a routing-failure body (Req 6). */
export class RoutingFailureError extends Error {
  readonly failure: RoutingFailure

  constructor(failure: RoutingFailure) {
    super(failure.message)
    this.name = 'RoutingFailureError'
    this.failure = failure
  }
}

/** Thrown for a non-2xx response that is not a routing failure. */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function parseError(response: Response): Promise<never> {
  let message = `Request failed with status ${response.status}`
  try {
    const body: unknown = await response.json()
    if (body && typeof body === 'object' && 'message' in body) {
      const maybeMessage = (body as { message: unknown }).message
      if (typeof maybeMessage === 'string' && maybeMessage.length > 0) {
        message = maybeMessage
      }
    }
  } catch {
    // Non-JSON error body; keep the default message.
  }
  throw new ApiError(response.status, message)
}

async function readJson<T>(response: Response): Promise<T> {
  return (await response.json()) as T
}

/** Creates a meeting (POST /meetings). */
export async function createMeeting(request: MeetingRequest): Promise<Meeting> {
  const response = await fetch(`${API_BASE}/meetings`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) {
    return parseError(response)
  }
  return readJson<Meeting>(response)
}

/** Retrieves a meeting by its URL code (GET /meetings/{code}); null on 404. */
export async function getMeeting(code: string): Promise<Meeting | null> {
  const response = await fetch(`${API_BASE}/meetings/${encodeURIComponent(code)}`, {
    headers: { Accept: 'application/json' },
  })
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    return parseError(response)
  }
  return readJson<Meeting>(response)
}

/** Edits a meeting (PUT /meetings/{code}). */
export async function editMeeting(code: string, request: MeetingRequest): Promise<Meeting> {
  const response = await fetch(`${API_BASE}/meetings/${encodeURIComponent(code)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) {
    return parseError(response)
  }
  return readJson<Meeting>(response)
}

/** Deletes a meeting (DELETE /meetings/{code}). */
export async function deleteMeeting(code: string): Promise<void> {
  const response = await fetch(`${API_BASE}/meetings/${encodeURIComponent(code)}`, {
    method: 'DELETE',
  })
  if (!response.ok && response.status !== 404) {
    await parseError(response)
  }
}

/**
 * Computes recommendations (POST /meetings/{code}/recommendations). On a 422
 * routing failure, throws {@link RoutingFailureError} carrying every affected
 * participant and reason so the UI can prompt a correction — never hiding a
 * participant (Requirement 6).
 */
export async function computeRecommendations(code: string): Promise<Recommendation> {
  const response = await fetch(
    `${API_BASE}/meetings/${encodeURIComponent(code)}/recommendations`,
    {
      method: 'POST',
      headers: { Accept: 'application/json' },
    },
  )
  if (response.status === 422) {
    const failure = await readJson<RoutingFailure>(response)
    throw new RoutingFailureError(failure)
  }
  if (!response.ok) {
    return parseError(response)
  }
  return readJson<Recommendation>(response)
}

/** Fetches address autocomplete suggestions (GET /geocode/autocomplete?q=). */
export async function autocompleteAddress(query: string): Promise<AddressSuggestion[]> {
  const trimmed = query.trim()
  if (trimmed.length === 0) {
    return []
  }
  const response = await fetch(
    `${API_BASE}/geocode/autocomplete?q=${encodeURIComponent(trimmed)}`,
    { headers: { Accept: 'application/json' } },
  )
  if (!response.ok) {
    return parseError(response)
  }
  return readJson<AddressSuggestion[]>(response)
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
  const response = await fetch(
    `${API_BASE}/geocode/resolve?q=${encodeURIComponent(trimmed)}`,
    { headers: { Accept: 'application/json' } },
  )
  if (response.status === 422) {
    return null
  }
  if (!response.ok) {
    return parseError(response)
  }
  return readJson<Coordinate>(response)
}
