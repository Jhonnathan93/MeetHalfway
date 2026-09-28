/**
 * TypeScript contracts mirroring the backend `/api/v1` JSON exactly
 * (see backend adapters/web/dto). All fields are camelCase, matching the
 * Java records. These types are the single source of truth for the API client
 * and components; no `any` is used anywhere.
 */

/** Shared transport mode wire values (lowercase), matching the backend. */
export type TransportMode = 'driving' | 'walking'

/** A geographic point as returned/accepted by the API. */
export interface Coordinate {
  lat: number
  lng: number
}

/** A participant supplied when creating or editing a meeting. */
export interface ParticipantInput {
  /** Optional display name; the backend sanitizes and may store empty. */
  name: string
  lat: number
  lng: number
}

/** Request body for POST /meetings and PUT /meetings/{code}. */
export interface MeetingRequest {
  participants: ParticipantInput[]
  transportMode: TransportMode
}

/** A stored participant in a meeting response (`ParticipantResponse`). */
export interface Participant {
  id: string
  name: string
  location: Coordinate
}

/** A single strategy result (`StrategyResultResponse`). */
export interface StrategyResult {
  point: Coordinate
  /** participant id -> travel time in whole minutes. */
  perParticipant: Record<string, number>
  sumTime: number
  maxTime: number
  stdDev: number
  /** Stable candidate identifier equal to this result's strategy key. */
  candidateId: StrategyKey
  /** Marks the strategy's single selected (recommended) point. */
  recommended: boolean
}

/**
 * A non-blocking coordinate warning (`CoordinateWarningResponse`). Reported
 * when a candidate point or participant origin is excluded/flagged; the
 * warning identifies the point rather than silently dropping it.
 */
export interface CoordinateWarning {
  kind: 'CANDIDATE' | 'PARTICIPANT_ORIGIN'
  /** candidateId or participantId the warning refers to. */
  reference: string
  lat: number
  lng: number
  reason: string
}

/** The three strategy results (`StrategyResultsResponse`). */
export interface StrategyResults {
  fastest: StrategyResult
  minimax: StrategyResult
  fairest: StrategyResult
}

/** The include-vs-exclude outlier trade-off (`OutlierTradeoffResponse`). */
export interface OutlierTradeoff {
  outliers: string[]
  including: StrategyResults
  excluding: StrategyResults
  avgTravelTimeIncluding: number
  avgTravelTimeExcluding: number
}

/** A successful recommendation (`RecommendationResponse`). */
export interface Recommendation {
  results: StrategyResults
  /** Present only when at least one outlier was detected; otherwise null. */
  outlierTradeoff: OutlierTradeoff | null
  /** Coordinate warnings for excluded/flagged points; may be empty. */
  warnings: CoordinateWarning[]
}

/** A meeting as returned by create/get/edit (`MeetingResponse`). */
export interface Meeting {
  urlCode: string
  participants: Participant[]
  transportMode: TransportMode
  /** Null until a computation has been persisted. */
  recommendation: Recommendation | null
}

/** A single unroutable participant location (`RoutingErrorResponse`). */
export interface RoutingError {
  participantId: string
  location: Coordinate
  reason: string
}

/** The HTTP 422 body when routing fails (`RoutingFailureResponse`). */
export interface RoutingFailure {
  code: string
  message: string
  errors: RoutingError[]
}

/** A geocoding autocomplete suggestion (`AddressSuggestionResponse`). */
export interface AddressSuggestion {
  description: string
  placeId: string
}

/** The three strategy keys, in display order. */
export const STRATEGY_KEYS = ['fastest', 'minimax', 'fairest'] as const
export type StrategyKey = (typeof STRATEGY_KEYS)[number]

/** Meeting participant-count bounds enforced in the UI (backend is authority). */
export const MIN_PARTICIPANTS = 2
export const MAX_PARTICIPANTS = 10
