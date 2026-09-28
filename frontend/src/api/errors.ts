/**
 * API error types shared across the frontend client modules
 * (`@/api/meetings`, `@/api/geocoding`) (Requirement 9.4).
 */
import type { RoutingFailure } from '@/types/Meeting'

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
