import { expect, test, type Page } from '@playwright/test'

const participants = [
  { id: 'p1', name: 'Ana', location: { lat: 6.2, lng: -75.57 } },
  { id: 'p2', name: 'Beto', location: { lat: 6.25, lng: -75.6 } },
]

const meeting = {
  urlCode: 'ABC12345',
  participants,
  transportMode: 'driving',
  recommendation: null,
}

const strategyResult = (candidateId: 'fastest' | 'minimax' | 'fairest', point: { lat: number; lng: number }) => ({
  point,
  perParticipant: { p1: 10, p2: 12 },
  sumTime: 22,
  maxTime: 12,
  stdDev: 1,
  candidateId,
  recommended: true,
})

const recommendations = {
  results: {
    fastest: strategyResult('fastest', { lat: 6.22, lng: -75.58 }),
    minimax: strategyResult('minimax', { lat: 6.23, lng: -75.59 }),
    fairest: strategyResult('fairest', { lat: 6.24, lng: -75.585 }),
  },
  outlierTradeoff: null,
  warnings: [],
}

async function mockGeocoding(page: Page): Promise<void> {
  await page.route('**/api/v1/geocode/autocomplete**', async (route) => {
    const query = new URL(route.request().url()).searchParams.get('q') ?? 'Address'
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify([{ description: `${query} result`, placeId: query }]),
    })
  })
  await page.route('**/api/v1/geocode/resolve**', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ lat: 6.2, lng: -75.57 }),
    })
  })
  await page.route('**tile.openstreetmap.org/**', (route) => route.abort())
}

async function fillMeetingForm(page: Page): Promise<void> {
  const names = page.locator('.meeting-form__name')
  const addresses = page.locator('.address-search__input')
  await names.nth(0).fill('Ana')
  await addresses.nth(0).fill('Poblado')
  const firstResolution = page.waitForResponse('**/api/v1/geocode/resolve**')
  await page.locator('.address-search__option').click()
  await firstResolution
  await names.nth(1).fill('Beto')
  await addresses.nth(1).fill('Laureles')
  const secondResolution = page.waitForResponse('**/api/v1/geocode/resolve**')
  await page.locator('.address-search__option').click()
  await secondResolution
}

test.beforeEach(async ({ page }) => {
  await mockGeocoding(page)
  await page.goto('/')
})

test('creates a meeting, calculates all strategies, and switches locale', async ({ page }) => {
  await page.getByLabel('Idioma').selectOption('en')
  await expect(page.getByText('Language')).toBeVisible()
  await page.getByLabel('Language').selectOption('es')

  await page.route('**/api/v1/meetings', async (route) => {
    expect(route.request().method()).toBe('POST')
    const payload = route.request().postDataJSON() as { participants: unknown[] }
    expect(payload.participants).toHaveLength(2)
    await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(meeting) })
  })
  await page.route('**/api/v1/meetings/**/recommendations', async (route) => {
    expect(route.request().method()).toBe('POST')
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(recommendations) })
  })

  await fillMeetingForm(page)
  await page.getByRole('button', { name: 'Buscar puntos de encuentro' }).click()

  await expect(page.getByRole('heading', { name: 'Puntos de encuentro recomendados' })).toBeVisible()
  await expect(page.locator('.strategy-card')).toHaveCount(3)
  await expect(page.locator('[data-testid="map-canvas"]')).toBeVisible()
})

test('surfaces routing failures without rendering recommendation cards', async ({ page }) => {
  await page.route('**/api/v1/meetings', async (route) => {
    await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(meeting) })
  })
  await page.route('**/api/v1/meetings/**/recommendations', async (route) => {
    await route.fulfill({
      status: 422,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'ROUTING_FAILURE',
        message: 'One or more participant locations could not be routed.',
        errors: [{ participantId: 'p2', location: participants[1]!.location, reason: 'No route found' }],
      }),
    })
  })

  await fillMeetingForm(page)
  await page.getByRole('button', { name: 'Buscar puntos de encuentro' }).click()

  await expect(page.locator('.routing-error')).toContainText('No route found')
  await expect(page.locator('.strategy-card')).toHaveCount(0)
  await expect(page.locator('.map-view__state--error')).toBeVisible()
})
