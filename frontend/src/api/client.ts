/**
 * Shared HTTP client for the MeetHalfway backend. It owns the base URL
 * (`/api/v1`), request transport, JSON (de)serialization, and error
 * normalization (Requirement 9.1, 9.6). It calls ONLY `/api/v1` endpoints; the
 * browser never talks to geocoding/routing providers directly, so no provider
 * secret ever reaches the frontend bundle (Requirement 11.4). In dev, Vite
 * proxies `/api/v1` to the backend (see vite.config.ts).
 *
 * Domain client modules (`meetings.ts`, `geocoding.ts`) build on this class and
 * preserve the existing status conventions (404 → null, 422 → routing failure,
 * etc.) by inspecting the returned `Response` before decoding.
 */
import { ApiError } from './errors'

const API_BASE = '/api/v1'

export class ApiClient {
  private readonly baseUrl: string

  constructor(baseUrl: string = API_BASE) {
    this.baseUrl = baseUrl
  }

  /** Builds an absolute API path from a base-relative one (e.g. `/meetings`). */
  private url(path: string): string {
    return `${this.baseUrl}${path}`
  }

  /** Performs a request against a base-relative path and returns the raw Response. */
  async request(path: string, init?: RequestInit): Promise<Response> {
    return fetch(this.url(path), init)
  }

  /** GET request (JSON Accept header) returning the raw Response. */
  async get(path: string): Promise<Response> {
    return this.request(path, { headers: { Accept: 'application/json' } })
  }

  /** POST request with an optional JSON body, returning the raw Response. */
  async post(path: string, body?: unknown): Promise<Response> {
    const init: RequestInit =
      body === undefined
        ? { method: 'POST', headers: { Accept: 'application/json' } }
        : {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
            body: JSON.stringify(body),
          }
    return this.request(path, init)
  }

  /** PUT request with a JSON body, returning the raw Response. */
  async put(path: string, body: unknown): Promise<Response> {
    return this.request(path, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(body),
    })
  }

  /** DELETE request returning the raw Response. */
  async delete(path: string): Promise<Response> {
    return this.request(path, { method: 'DELETE' })
  }

  /** Deserializes a JSON response body as `T`. */
  async readJson<T>(response: Response): Promise<T> {
    return (await response.json()) as T
  }

  /**
   * Normalizes a non-2xx response into an {@link ApiError}, preferring the
   * backend `message` field when present. Always throws.
   */
  async parseError(response: Response): Promise<never> {
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
}

/** Shared singleton used by the domain client modules. */
export const apiClient = new ApiClient()
