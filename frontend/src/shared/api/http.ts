import { ApiError } from './errors'

const API_BASE = '/api/v1'

/** Sends an API request with a same-origin base path and JSON accept header. */
export function apiRequest(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  return fetch(`${API_BASE}${path}`, { ...init, headers })
}

/** Builds the common JSON request options used by typed feature API functions. */
export function jsonBody(method: 'POST' | 'PUT', body: unknown): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }
}

/** Reads a successful JSON response or throws an ApiError with the backend message. */
export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await apiRequest(path, init)
  if (!response.ok) {
    throw await apiError(response)
  }
  return response.json() as Promise<T>
}

/** Converts an error response into the frontend's consistent error type. */
export async function apiError(response: Response): Promise<ApiError> {
  let message = `Request failed with status ${response.status}`
  try {
    const body: unknown = await response.json()
    if (body && typeof body === 'object' && 'message' in body) {
      const value = (body as { message: unknown }).message
      if (typeof value === 'string' && value.length > 0) message = value
    }
  } catch {
    // Keep the status-based message when the backend body is empty or non-JSON.
  }
  return new ApiError(response.status, message)
}
