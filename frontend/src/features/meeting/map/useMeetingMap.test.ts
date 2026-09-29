import { beforeEach, describe, expect, it, vi } from 'vitest'

const leafletMocks = vi.hoisted(() => ({
  map: vi.fn(),
  tileLayer: vi.fn(),
  layerGroup: vi.fn(),
}))

vi.mock('leaflet', () => ({
  default: {
    Icon: { Default: { prototype: {}, mergeOptions: vi.fn() } },
    map: leafletMocks.map,
    tileLayer: leafletMocks.tileLayer,
    layerGroup: leafletMocks.layerGroup,
  },
}))

import { useMeetingMap } from './useMeetingMap'

describe('useMeetingMap lifecycle', () => {
  beforeEach(() => {
    leafletMocks.map.mockReset()
    leafletMocks.tileLayer.mockReset().mockReturnValue({ addTo: vi.fn() })
    leafletMocks.layerGroup.mockReset().mockReturnValue({
      addTo: vi.fn(() => ({ clearLayers: vi.fn() })),
    })
  })

  it('recreates Leaflet when a later search provides a fresh canvas element', () => {
    const firstLeafletMap = { remove: vi.fn() }
    const secondLeafletMap = { remove: vi.fn() }
    leafletMocks.map
      .mockReturnValueOnce(firstLeafletMap)
      .mockReturnValueOnce(secondLeafletMap)

    const controller = useMeetingMap()
    const firstCanvas = document.createElement('div')
    const secondCanvas = document.createElement('div')

    controller.mount(firstCanvas)
    controller.mount(firstCanvas)
    expect(leafletMocks.map).toHaveBeenCalledTimes(1)

    // The first map is bound to the old canvas, removed while results reload.
    controller.mount(secondCanvas)

    expect(firstLeafletMap.remove).toHaveBeenCalledTimes(1)
    expect(leafletMocks.map).toHaveBeenCalledTimes(2)
    expect(leafletMocks.map.mock.calls[0]?.[0]).toBe(firstCanvas)
    expect(leafletMocks.map.mock.calls[1]?.[0]).toBe(secondCanvas)

    controller.destroy()
    expect(secondLeafletMap.remove).toHaveBeenCalledTimes(1)
  })
})
