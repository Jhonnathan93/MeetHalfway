import { describe, it, expect } from 'vitest'
import fc from 'fast-check'
import { formatMinutes, formatMetric } from './formatMinutes'

/**
 * Property-based test for the pure metric formatters (Task 14.6). Runs a
 * minimum of 100 iterations per property.
 */
describe('formatMinutes (property-based)', () => {
  it('always renders whole, non-negative minutes ending in the unit token', () => {
    fc.assert(
      fc.property(fc.integer({ min: 0, max: 100000 }), fc.constantFrom('min', 'mín'), (m, unit) => {
        const out = formatMinutes(m, unit)
        expect(out.endsWith(unit)).toBe(true)
        expect(out.includes('-')).toBe(false)
        expect(out.startsWith(String(m))).toBe(true)
      }),
      { numRuns: 100 },
    )
  })

  it('clamps negative or fractional inputs to whole non-negative minutes', () => {
    fc.assert(
      fc.property(fc.double({ min: -1000, max: 1000, noNaN: true }), (value) => {
        const out = formatMinutes(value)
        const numeric = Number.parseInt(out, 10)
        expect(Number.isInteger(numeric)).toBe(true)
        expect(numeric).toBeGreaterThanOrEqual(0)
      }),
      { numRuns: 100 },
    )
  })

  it('formatMetric never returns an empty string for finite input', () => {
    fc.assert(
      fc.property(fc.double({ min: -10000, max: 10000, noNaN: true }), (value) => {
        expect(formatMetric(value).length).toBeGreaterThan(0)
      }),
      { numRuns: 100 },
    )
  })
})
