/**
 * Pure formatting helpers for travel-time metrics. Kept side-effect-free and
 * dependency-free so they are unit- and property-testable (fast-check).
 */

/**
 * Formats a whole-minute travel time as a compact human string.
 *
 * Invariants (property-tested): the input is treated as whole, non-negative
 * minutes; the result always ends in the minutes unit token, is non-empty, and
 * never contains a negative sign for valid input.
 *
 * @param minutes whole, non-negative minutes
 * @param unit    the localized minutes unit token (e.g. "min")
 */
export function formatMinutes(minutes: number, unit = 'min'): string {
  const safe = Number.isFinite(minutes) ? Math.max(0, Math.trunc(minutes)) : 0
  return `${safe} ${unit}`
}

/**
 * Rounds a metric (which may be a fractional double like sumTime or stdDev) to
 * one decimal place for display, without a trailing ".0" for whole values.
 *
 * @param value a finite metric value
 */
export function formatMetric(value: number): string {
  if (!Number.isFinite(value)) {
    return '0'
  }
  const rounded = Math.round(value * 10) / 10
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1)
}
