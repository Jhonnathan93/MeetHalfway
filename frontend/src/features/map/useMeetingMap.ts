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

import type { Coordinate, StrategyKey } from '@/types/Meeting'

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
  /** The strategy key this candidate maps to (`fastest`|`minimax`|`fairest`). */
  candidateId: StrategyKey
  point: Coordinate
  /** True for the recommended point, false for alternatives (R11.2). */
  recommended: boolean
  /** Pre-rendered popup HTML: candidateId + sumTime/maxTime/stdDev (R11.4). */
  popupHtml: string
}

/** A participant-origin point ready for rendering. */
export interface OriginMarker {
  /** Stable participant id, used as an accessible label / popup title. */
  participantId: string
  label: string
  point: Coordinate
}

/** Everything the composable needs to (re)draw the map. */
export interface MeetingMapData {
  candidates: CandidateMarker[]
  origins: OriginMarker[]
}

/** Tile layer configuration; OpenStreetMap raster tiles (free, no key). */
const TILE_URL = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
const TILE_ATTRIBUTION = '© OpenStreetMap contributors'

/** Fallback view when there are no valid markers to fit (world view). */
const FALLBACK_CENTER: L.LatLngExpression = [6.2442, -75.5812] // Medellín
const FALLBACK_ZOOM = 11
const MAX_FIT_ZOOM = 16

/**
 * CSS class markers carry so tests and styles can target them. Candidate
 * markers are visually distinguished from origins, and the recommended
 * candidate from alternatives, via these classes on a `divIcon`.
 */
export const MARKER_CLASS = {
  recommended: 'map-marker map-marker--recommended',
  alternative: 'map-marker map-marker--alternative',
  origin: 'map-marker map-marker--origin',
} as const

function candidateIcon(recommended: boolean): L.DivIcon {
  return L.divIcon({
    className: recommended ? MARKER_CLASS.recommended : MARKER_CLASS.alternative,
    iconSize: recommended ? [28, 28] : [22, 22],
    iconAnchor: recommended ? [14, 14] : [11, 11],
  })
}

function originIcon(): L.DivIcon {
  return L.divIcon({
    className: MARKER_CLASS.origin,
    iconSize: [16, 16],
    iconAnchor: [8, 8],
  })
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

  function mount(container: HTMLElement): void {
    if (map) {
      return
    }
    map = L.map(container, {
      center: FALLBACK_CENTER,
      zoom: FALLBACK_ZOOM,
      // Keep interaction minimal (pan/zoom); no advanced GIS controls (R11.10).
      attributionControl: true,
    })
    L.tileLayer(TILE_URL, { attribution: TILE_ATTRIBUTION, maxZoom: 19 }).addTo(map)
    markerLayer = L.layerGroup().addTo(map)
  }

  function clear(): void {
    markerLayer?.clearLayers()
  }

  function render(data: MeetingMapData): void {
    if (!map || !markerLayer) {
      return
    }
    clear()

    const bounds = L.latLngBounds([])
    let hasMarker = false

    for (const candidate of data.candidates) {
      if (!isValidCoordinate(candidate.point)) {
        continue
      }
      const latLng: L.LatLngExpression = [candidate.point.lat, candidate.point.lng]
      L.marker(latLng, {
        icon: candidateIcon(candidate.recommended),
        zIndexOffset: candidate.recommended ? 1000 : 0,
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
      L.marker(latLng, { icon: originIcon(), title: origin.label })
        .bindPopup(origin.label)
        .addTo(markerLayer)
      bounds.extend(latLng)
      hasMarker = true
    }

    // Auto-fit bounds over every valid marker (R11.3); fall back to the default
    // view when nothing valid is on the map.
    if (hasMarker && bounds.isValid()) {
      map.fitBounds(bounds, { padding: [32, 32], maxZoom: MAX_FIT_ZOOM })
    } else {
      map.setView(FALLBACK_CENTER, FALLBACK_ZOOM)
    }
  }

  function destroy(): void {
    markerLayer?.clearLayers()
    markerLayer = null
    map?.remove()
    map = null
  }

  return { mount, render, clear, destroy }
}
