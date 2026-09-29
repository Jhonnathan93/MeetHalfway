import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createI18n } from 'vue-i18n'
import { messages, type Locale, type MessageSchema } from '@/shared/i18n/messages'
import type {
  CoordinateWarning,
  Participant,
  StrategyKey,
  StrategyResult,
  StrategyResults,
} from '@/features/meeting/types'
import type { MeetingMapController, MeetingMapData } from './useMeetingMap'

/**
 * Tests for MapView (R11 + R13.5).
 *
 * Marker rendering by strategy, origin markers,
 * bounds fitting, and popup content are Leaflet concerns that are flaky to
 * assert on the raw DOM under jsdom, so we spy on the `useMeetingMap`
 * controller (mount/render/clear) and assert MapView drives it with the
 * expected candidate/origin marker data. State behavior (loading, error, empty,
 * excluded-coordinate notice) is asserted on the rendered component DOM/text.
 */

// A stable, inspectable controller double. MapView calls mount/render/clear on
// it, letting us assert the marker data it derives from props without exercising
// real Leaflet DOM APIs (R11.1–R11.5, R11.9).
const controllerSpy: MeetingMapController & {
  mount: ReturnType<typeof vi.fn>
  render: ReturnType<typeof vi.fn>
  clear: ReturnType<typeof vi.fn>
  destroy: ReturnType<typeof vi.fn>
} = {
  mount: vi.fn(),
  render: vi.fn(),
  clear: vi.fn(),
  destroy: vi.fn(),
}

vi.mock('./useMeetingMap', async () => {
  const actual = await vi.importActual<typeof import('./useMeetingMap')>('./useMeetingMap')
  return {
    ...actual,
    // Keep the real validity helpers/types; only replace the controller factory
    // so the component talks to our inspectable double.
    useMeetingMap: () => controllerSpy,
  }
})

// Imported after the mock is registered so MapView picks up the spied factory.
import MapView, { type MapPhase } from './MapView.vue'

function makeI18n(locale: Locale = 'en') {
  return createI18n<[MessageSchema], Locale>({
    legacy: false,
    locale,
    fallbackLocale: 'en',
    messages,
  })
}

function strategy(
  candidateId: StrategyKey,
  point: { lat: number; lng: number },
  recommended: boolean,
  overrides: Partial<StrategyResult> = {},
): StrategyResult {
  return {
    point,
    perParticipant: { a: 10, b: 20 },
    sumTime: 30,
    maxTime: 20,
    stdDev: 5,
    candidateId,
    recommended,
    ...overrides,
  }
}

// Three selected points, one per recommendation strategy.
function makeResults(): StrategyResults {
  return {
    fastest: strategy('fastest', { lat: 6.25, lng: -75.56 }, true, {
      sumTime: 30,
      maxTime: 18,
      stdDev: 4.2,
    }),
    minimax: strategy('minimax', { lat: 6.24, lng: -75.57 }, false),
    fairest: strategy('fairest', { lat: 6.26, lng: -75.55 }, false),
  }
}

function makeParticipants(count: number): Participant[] {
  return Array.from({ length: count }, (_, i) => ({
    id: `p${i + 1}`,
    name: `Person ${i + 1}`,
    location: { lat: 6.2 + i * 0.01, lng: -75.6 + i * 0.01 },
  }))
}

interface MountOptions {
  phase?: MapPhase
  results?: StrategyResults | null
  participants?: Participant[]
  warnings?: CoordinateWarning[]
  errorMessage?: string
  locale?: Locale
}

function mountMapView(opts: MountOptions = {}) {
  // Build props without an explicit `errorMessage: undefined`, which
  // `exactOptionalPropertyTypes` forbids for an optional prop.
  const props: {
    phase: MapPhase
    results: StrategyResults | null
    participants: Participant[]
    warnings: CoordinateWarning[]
    errorMessage?: string
  } = {
    phase: opts.phase ?? 'success',
    results: opts.results ?? null,
    participants: opts.participants ?? [],
    warnings: opts.warnings ?? [],
  }
  if (opts.errorMessage !== undefined) {
    props.errorMessage = opts.errorMessage
  }
  return mount(MapView, {
    global: { plugins: [makeI18n(opts.locale ?? 'en')] },
    props,
  })
}

/**
 * MapView's watcher uses `flush: 'post'`, so mount/render/clear run after the
 * next DOM tick rather than synchronously during mount(). Await this so the
 * controller spy has been driven before asserting on it.
 */
async function settle(): Promise<void> {
  await nextTick()
  await flushPromises()
}

/** Grabs the single render() call payload MapView passed to the controller. */
function lastRender(): MeetingMapData {
  const calls = controllerSpy.render.mock.calls
  expect(calls.length).toBeGreaterThan(0)
  return calls[calls.length - 1]![0] as MeetingMapData
}

beforeEach(() => {
  controllerSpy.mount.mockClear()
  controllerSpy.render.mockClear()
  controllerSpy.clear.mockClear()
  controllerSpy.destroy.mockClear()
})

describe('MapView candidate markers (R11.1, R11.2, R11.4)', () => {
  it('renders exactly one candidate marker per strategy point (three) — R11.1', async () => {
    mountMapView({ phase: 'success', results: makeResults(), participants: makeParticipants(2) })
    await settle()

    const data = lastRender()
    expect(data.candidates).toHaveLength(3)
    expect(data.candidates.map((c) => c.strategy).sort()).toEqual([
      'fairest',
      'fastest',
      'minimax',
    ])
  })

  it('preserves strategy identity for each selected point', async () => {
    mountMapView({ phase: 'success', results: makeResults(), participants: makeParticipants(2) })
    await settle()

    const data = lastRender()
    expect(data.candidates.map((candidate) => candidate.strategy).sort()).toEqual([
      'fairest', 'fastest', 'minimax',
    ])
  })

  it('builds popup content with the strategy and metrics — R11.4', async () => {
    mountMapView({ phase: 'success', results: makeResults(), participants: makeParticipants(2) })
    await settle()

    const data = lastRender()
    const fastest = data.candidates.find((c) => c.strategy === 'fastest')!
    // Popup carries the strategy label plus Σ / max / σ metrics (localized EN).
    expect(fastest.popupHtml).toContain('Fastest')
    expect(fastest.popupHtml).toContain('Total travel time')
    expect(fastest.popupHtml).toContain('Longest single trip')
    expect(fastest.popupHtml).toContain('Travel-time spread')
    // Metric values from the fastest strategy are present.
    expect(fastest.popupHtml).toContain('30') // sumTime
    expect(fastest.popupHtml).toContain('18') // maxTime
    expect(fastest.popupHtml).toContain('4.2') // stdDev
  })
})

describe('MapView origin markers (R11.5)', () => {
  it('renders one origin marker per participant, distinct from candidates (2 participants) — R11.5', async () => {
    mountMapView({ phase: 'success', results: makeResults(), participants: makeParticipants(2) })
    await settle()

    const data = lastRender()
    expect(data.origins).toHaveLength(2)
    // Origins carry participant identity and are a separate collection from
    // candidates (different marker treatment is applied by the composable).
    expect(data.origins.map((o) => o.participantId).sort()).toEqual(['p1', 'p2'])
    const strategies = new Set(data.candidates.map((candidate) => candidate.strategy))
    expect(data.origins.every((origin) => !strategies.has(origin.participantId as StrategyKey))).toBe(true)
  })

  it('renders up to 10 origin markers for a 10-participant meeting — R11.5', async () => {
    mountMapView({ phase: 'success', results: makeResults(), participants: makeParticipants(10) })
    await settle()

    const data = lastRender()
    expect(data.origins).toHaveLength(10)
  })

  it('falls back to the participant id as the label when the name is empty — R11.5', async () => {
    const participants: Participant[] = [
      { id: 'p1', name: '', location: { lat: 6.2, lng: -75.6 } },
      { id: 'p2', name: 'Beto', location: { lat: 6.21, lng: -75.61 } },
    ]
    mountMapView({ phase: 'success', results: makeResults(), participants })
    await settle()

    const data = lastRender()
    expect(data.origins.find((o) => o.participantId === 'p1')!.label).toBe('p1')
    expect(data.origins.find((o) => o.participantId === 'p2')!.label).toBe('Beto')
  })
})

describe('MapView bounds fitting (R11.3)', () => {
  it('mounts the map and renders markers so the composable can fit bounds — R11.3', async () => {
    mountMapView({ phase: 'success', results: makeResults(), participants: makeParticipants(3) })
    await settle()

    // MapView mounts the Leaflet map on the canvas and hands the full marker set
    // to render(); the composable fits bounds over every valid marker.
    expect(controllerSpy.mount).toHaveBeenCalledTimes(1)
    const data = lastRender()
    expect(data.candidates.length + data.origins.length).toBe(6)
  })

  it('exposes the map canvas test hook in the success state', () => {
    const wrapper = mountMapView({
      phase: 'success',
      results: makeResults(),
      participants: makeParticipants(2),
    })
    expect(wrapper.find('[data-testid="map-canvas"]').exists()).toBe(true)
  })
})

describe('MapView loading state (R11.8, R11.9)', () => {
  it('shows a loading status and hides the map canvas — R11.8', async () => {
    const wrapper = mountMapView({
      phase: 'loading',
      results: null,
      participants: makeParticipants(2),
    })
    await settle()

    expect(wrapper.find('[role="status"]').text()).toBe(messages.en.map.loading)
    // No canvas while loading; markers are never rendered as current results.
    expect(wrapper.find('[data-testid="map-canvas"]').exists()).toBe(false)
    expect(controllerSpy.render).not.toHaveBeenCalled()
  })

  it('clears stale markers when a success response is followed by a loading request — R11.9', async () => {
    const wrapper = mountMapView({
      phase: 'success',
      results: makeResults(),
      participants: makeParticipants(2),
    })
    await settle()
    // A success render happened; now a new request starts.
    expect(controllerSpy.render).toHaveBeenCalled()
    controllerSpy.clear.mockClear()

    await wrapper.setProps({ phase: 'loading' })
    await settle()

    // The previous response's markers are cleared, not shown as current.
    expect(controllerSpy.clear).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="map-canvas"]').exists()).toBe(false)
  })
})

describe('MapView error state (R11.9)', () => {
  it('shows the default error alert and hides the map canvas — R11.9', async () => {
    const wrapper = mountMapView({ phase: 'error', results: makeResults() })
    await settle()

    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toBe(messages.en.map.error)
    expect(controllerSpy.render).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="map-canvas"]').exists()).toBe(false)
  })

  it('clears stale markers when a success response is followed by a failure — R11.9', async () => {
    const wrapper = mountMapView({
      phase: 'success',
      results: makeResults(),
      participants: makeParticipants(2),
    })
    await settle()
    expect(controllerSpy.render).toHaveBeenCalled()
    controllerSpy.clear.mockClear()

    await wrapper.setProps({ phase: 'error' })
    await settle()

    // Markers from the previous response are cleared on failure (R11.9).
    expect(controllerSpy.clear).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="map-canvas"]').exists()).toBe(false)
  })

  it('shows the provided error message when one is supplied — R11.9', async () => {
    const wrapper = mountMapView({
      phase: 'error',
      results: null,
      errorMessage: 'Routing failed for two participants',
    })
    await settle()
    expect(wrapper.find('[role="alert"]').text()).toBe('Routing failed for two participants')
  })
})

describe('MapView empty state (R11.6)', () => {
  it('shows an explicit empty-state message and no canvas when there are no candidates — R11.6', async () => {
    const wrapper = mountMapView({
      phase: 'success',
      results: null,
      participants: makeParticipants(2),
    })
    await settle()

    expect(wrapper.find('[role="status"]').text()).toBe(messages.en.map.empty)
    expect(wrapper.find('[data-testid="map-canvas"]').exists()).toBe(false)
    // With no candidates the map is not mounted and no markers are rendered.
    expect(controllerSpy.render).not.toHaveBeenCalled()
  })
})

describe('MapView invalid-coordinate exclusion notice (R11.7)', () => {
  it('surfaces a non-blocking notice mirroring backend warnings — R11.7', () => {
    const warnings: CoordinateWarning[] = [
      {
        kind: 'CANDIDATE',
        reference: 'fairest',
        lat: 999,
        lng: -75.55,
        reason: 'latitude out of range',
      },
      {
        kind: 'PARTICIPANT_ORIGIN',
        reference: 'p2',
        lat: 6.24,
        lng: -999,
        reason: 'longitude out of range',
      },
    ]
    const wrapper = mountMapView({
      phase: 'success',
      results: makeResults(),
      participants: makeParticipants(2),
      warnings,
    })

    const notice = wrapper.find('[aria-label="excluded-points"]')
    expect(notice.exists()).toBe(true)
    expect(notice.text()).toContain(messages.en.map.excludedHeading)
    // Both the candidate and origin exclusions are listed (never silently dropped).
    expect(notice.text()).toContain('Candidate')
    expect(notice.text()).toContain('fairest')
    expect(notice.text()).toContain('Origin')
    expect(notice.text()).toContain('p2')
    // The map still renders the valid points alongside the notice.
    expect(wrapper.find('[data-testid="map-canvas"]').exists()).toBe(true)
  })

  it('adds a notice entry for a locally out-of-range candidate coordinate — R11.7', () => {
    const results = makeResults()
    results.minimax.point = { lat: 91, lng: -75.57 } // latitude out of [-90, 90]
    const wrapper = mountMapView({
      phase: 'success',
      results,
      participants: makeParticipants(2),
    })

    const notice = wrapper.find('[aria-label="excluded-points"]')
    expect(notice.exists()).toBe(true)
    expect(notice.text()).toContain('minimax')
  })

  it('renders no notice when every coordinate is valid — R11.7', () => {
    const wrapper = mountMapView({
      phase: 'success',
      results: makeResults(),
      participants: makeParticipants(2),
    })
    expect(wrapper.find('[aria-label="excluded-points"]').exists()).toBe(false)
  })
})
