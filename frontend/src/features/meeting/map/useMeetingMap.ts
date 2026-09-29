/**
 * Leaflet map composable for the meeting-results visualization.
 *
 * This composable is view-only: it renders the candidate points, recommended
 * point, and participant origins the backend already computed and never
 * recomputes points, routing, or scores (R10.1). It owns the Leaflet map
 * lifecycle (create/destroy), marker rendering, bounds fitting, popups, and
 * validity filtering. All user-visible copy is passed in already localized by
 * the caller (vue-i18n lives in the component), so this module stays free of
 * i18n and Vue-template concerns.
 *
 * Scope guard (R11.10): marker rendering, bounds fitting, and marker
 * inspection only — no advanced GIS.
 */
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

// Vite marker-asset fix: Leaflet's default icon resolves its image URLs
// relative to the CSS at runtime, which breaks under Vite bundling. Import the
// asset URLs explicitly and rebind them on the default icon prototype so
// markers render (design.md implementation note).
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'

import type { Coordinate, StrategyKey } from '@/features/meeting/types'

// Rebind once at module load so every default marker created afterwards finds
// its assets. Casting to a mutable record avoids the read-only prototype typing
// while staying `any`-free.
const iconDefaultPrototype = L.Icon.Default.prototype as unknown as {
  _getIconUrl?: unknown
}
delete iconDefaultPrototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
})

/** Latitude is valid only within the inclusive range [-90, 90] (R11.7). */
export function isValidLat(lat: number): boolean {
  return Number.isFinite(lat) && lat >= -90 && lat <= 90
}

/** Longitude is valid only within the inclusive range [-180, 180] (R11.7). */
export function isValidLng(lng: number): boolean {
  return Number.isFinite(lng) && lng >= -180 && lng <= 180
}

/** A coordinate renders only when both its latitude and longitude are valid. */
export function isValidCoordinate(point: Coordinate): boolean {
  return isValidLat(point.lat) && isValidLng(point.lng)
}

/** A candidate point ready for rendering (already localized/derived data). */
export interface CandidateMarker {
  /** Which of the three recommendation strategies selected this point. */
  strategy: StrategyKey
  point: Coordinate
  /** Localized strategy name and travel metrics for the marker popup. */
  popupHtml: string
}

/** A participant-origin point ready for rendering. */
export interface OriginMarker {
  /** Stable participant id, used as an accessible label / popup title. */
  participantId: string
  label: string
  initials: string
  point: Coordinate
  selected: boolean
}

/** Everything the composable needs to (re)draw the map. */
export interface MeetingMapData {
  candidates: CandidateMarker[]
  origins: OriginMarker[]
  onOriginSelect?: (participantId: string) => void
}

/** Tile layer configuration; OpenStreetMap raster tiles (free, no key). */
const TILE_URL = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
const TILE_ATTRIBUTION = '© OpenStreetMap contributors'

/** Neutral world view used when there are no valid markers to fit. */
const FALLBACK_CENTER: L.LatLngExpression = [0, 0]
const FALLBACK_ZOOM = 2
const MAX_FIT_ZOOM = 16

/**
 * CSS class markers carry so tests and styles can target them. Candidate
 * markers are visually distinguished from origins and colored by strategy.
 */
export const MARKER_CLASS = {
  fastest: 'map-marker map-marker--fastest',
  minimax: 'map-marker map-marker--minimax',
  fairest: 'map-marker map-marker--fairest',
  origin: 'map-marker map-marker--origin',
} as const

function candidateIcon(strategy: StrategyKey): L.DivIcon {
  return L.divIcon({
    className: MARKER_CLASS[strategy],
    html: `<span>${strategy.slice(0, 1).toUpperCase()}</span>`,
    iconSize: [32, 32],
    iconAnchor: [16, 16],
  })
}

function originIcon(origin: OriginMarker): L.DivIcon {
  return L.divIcon({
    className: `${MARKER_CLASS.origin}${origin.selected ? ' map-marker--origin-selected' : ''}`,
    html: `<span>${escapeMarkerLabel(origin.initials)}</span>`,
    iconSize: [34, 34],
    iconAnchor: [17, 17],
  })
}

function escapeMarkerLabel(value: string): string {
  const escaped: Record<string, string> = {
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }
  return value.replace(/[&<>"']/g, (character) => escaped[character] ?? character)
}

/**
 * The controller returned to the component. `mount` binds the map to a DOM
 * element, `render` (re)draws markers from backend data, `clear` removes all
 * markers (used by the error state so a previous response is never shown as
 * current, R11.9), and `destroy` tears down the Leaflet instance.
 */
export interface MeetingMapController {
  mount(container: HTMLElement): void
  render(data: MeetingMapData): void
  clear(): void
  destroy(): void
}

/**
 * Creates a meeting-map controller. The map instance is created lazily on
 * `mount` so the composable can be constructed in setup() before the DOM ref
 * is available.
 */
export function useMeetingMap(): MeetingMapController {
  let map: L.Map | null = null
  let markerLayer: L.LayerGroup | null = null
  let mountedContainer: HTMLElement | null = null
  let lastBoundsFingerprint = ''

  function mount(container: HTMLElement): void {
    if (map && mountedContainer === container) {
      return
    }
    // MapView temporarily removes the canvas from the DOM while a new search
    // is loading. On success Vue creates a fresh element; a Leaflet instance
    // cannot be reused with that different container.
    if (map) {
      destroy()
    }
    map = L.map(container, {
      center: FALLBACK_CENTER,
      zoom: FALLBACK_ZOOM,
      // Keep interaction minimal (pan/zoom); no advanced GIS controls (R11.10).
      attributionControl: true,
    })
    mountedContainer = container
    L.tileLayer(TILE_URL, { attribution: TILE_ATTRIBUTION, maxZoom: 19 }).addTo(map)
    markerLayer = L.layerGroup().addTo(map)
  }

  function clear(): void {
    markerLayer?.clearLayers()
    lastBoundsFingerprint = ''
  }

  function render(data: MeetingMapData): void {
    if (!map || !markerLayer) {
      return
    }
    // Re-render markers without resetting the bounds fingerprint: selecting a
    // person changes marker treatment but should not yank the user's map view.
    markerLayer.clearLayers()

    const bounds = L.latLngBounds([])
    let hasMarker = false

    for (const candidate of data.candidates) {
      if (!isValidCoordinate(candidate.point)) {
        continue
      }
      const latLng: L.LatLngExpression = [candidate.point.lat, candidate.point.lng]
      L.marker(latLng, {
        icon: candidateIcon(candidate.strategy),
      })
        .bindPopup(candidate.popupHtml)
        .addTo(markerLayer)
      bounds.extend(latLng)
      hasMarker = true
    }

    for (const origin of data.origins) {
      if (!isValidCoordinate(origin.point)) {
        continue
      }
      const latLng: L.LatLngExpression = [origin.point.lat, origin.point.lng]
      L.marker(latLng, { icon: originIcon(origin), title: origin.label, keyboard: true })
        .bindTooltip(origin.label, { direction: 'top', offset: [0, -12] })
        .on('click', () => data.onOriginSelect?.(origin.participantId))
        .addTo(markerLayer)
      bounds.extend(latLng)
      hasMarker = true
    }

    // Auto-fit bounds over every valid marker (R11.3); fall back to the default
    // view when nothing valid is on the map.
    const boundsFingerprint = [
      ...data.candidates.map(({ point }) => `${point.lat},${point.lng}`),
      ...data.origins.map(({ point }) => `${point.lat},${point.lng}`),
    ].join('|')
    if (hasMarker && bounds.isValid() && boundsFingerprint !== lastBoundsFingerprint) {
      map.fitBounds(bounds, { padding: [32, 32], maxZoom: MAX_FIT_ZOOM })
      lastBoundsFingerprint = boundsFingerprint
    } else {
      if (!hasMarker) {
        map.setView(FALLBACK_CENTER, FALLBACK_ZOOM)
        lastBoundsFingerprint = ''
      }
    }
  }

  function destroy(): void {
    markerLayer?.clearLayers()
    markerLayer = null
    map?.remove()
    map = null
    mountedContainer = null
  }

  return { mount, render, clear, destroy }
}
