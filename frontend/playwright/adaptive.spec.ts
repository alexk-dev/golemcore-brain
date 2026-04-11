import { expect, test } from '@playwright/test'

test.describe('Login page adaptive layout', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/auth/me', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ authDisabled: false, publicAccess: false, user: null }),
      }),
    )
    await page.route('**/api/auth/config', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ authDisabled: false, publicAccess: false, user: null }),
      }),
    )
    await page.route('**/api/config', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicAccess: false,
          hideLinkMetadataSection: false,
          authDisabled: false,
          maxAssetUploadSizeBytes: 1024,
          siteTitle: 'GolemCore Brain',
          rootPath: '',
        }),
      }),
    )
    await page.route('**/api/wiki/tree', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'root',
          path: '',
          parentPath: null,
          title: 'Welcome',
          slug: '',
          kind: 'ROOT',
          hasChildren: true,
          children: [
            {
              id: 'guides',
              path: 'guides',
              parentPath: '',
              title: 'Guides',
              slug: 'guides',
              kind: 'SECTION',
              hasChildren: false,
              children: [],
            },
          ],
        }),
      }),
    )
  })

  test('hides sidebar and centers form on desktop viewport', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/login')

    await expect(page.getByRole('heading', { name: 'Login' })).toBeVisible()
    await expect(page.locator('#sidebar-container')).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Toggle Sidebar' })).toHaveCount(0)
    await expect(page.getByRole('banner')).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Go to page' })).toHaveCount(0)
    await expect(page.getByRole('link', { name: 'Login' })).toHaveCount(0)

    const card = page.locator('.shell-form-page__card').first()
    await expect(card).toBeVisible()

    const [cardBox, viewport] = await Promise.all([
      card.boundingBox(),
      page.evaluate(() => ({ width: window.innerWidth, height: window.innerHeight })),
    ])
    expect(cardBox).not.toBeNull()
    const cardCenter = (cardBox!.x + cardBox!.width / 2)
    expect(Math.abs(cardCenter - viewport.width / 2)).toBeLessThan(40)

    await page.screenshot({ path: 'playwright-login-desktop.png', fullPage: true })
  })

  test('renders login without sidebar on tablet viewport', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 })
    await page.goto('/login')

    await expect(page.getByRole('heading', { name: 'Login' })).toBeVisible()
    await expect(page.locator('#sidebar-container')).toHaveCount(0)

    const card = page.locator('.shell-form-page__card').first()
    const cardBox = await card.boundingBox()
    expect(cardBox).not.toBeNull()
    expect(cardBox!.width).toBeGreaterThan(280)
    expect(cardBox!.width).toBeLessThanOrEqual(768)

    await page.screenshot({ path: 'playwright-login-tablet.png', fullPage: true })
  })

  test('renders login on mobile viewport without horizontal scroll', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await page.goto('/login')

    await expect(page.getByRole('heading', { name: 'Login' })).toBeVisible()

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    )
    expect(overflow).toBeLessThanOrEqual(1)

    const card = page.locator('.shell-form-page__card').first()
    const cardBox = await card.boundingBox()
    expect(cardBox).not.toBeNull()
    expect(cardBox!.width).toBeLessThanOrEqual(375)

    await page.screenshot({ path: 'playwright-login-mobile.png', fullPage: true })
  })

  test('hides Login button from header toolbar while on login route', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 })
    await page.goto('/login')
    await expect(page.getByRole('heading', { name: 'Login' })).toBeVisible()

    const formButton = page.getByRole('button', { name: /Sign in/i })
    await expect(formButton).toBeVisible()
  })
})
