import { expect, test } from '@playwright/test'

test.skip(
  !process.env.PLAYWRIGHT_BASE_URL,
  'Requires a running backend; set PLAYWRIGHT_BASE_URL to the brain app URL.',
)

function appBasePath() {
  return new URL(process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost').pathname.replace(/\/+$/, '')
}

function appPath(path: string) {
  const basePath = appBasePath()
  return `${basePath}${path}`
}

function isAppRoot(url: URL) {
  return url.pathname.replace(/\/+$/, '') === appBasePath()
}

async function signIn(page: import('@playwright/test').Page) {
  await page.goto(appPath('/login'))
  await page.getByLabel('Username or email').fill('admin')
  await page.getByLabel('Password').fill('admin')
  await Promise.all([
    page.waitForURL(isAppRoot),
    page.getByRole('button', { name: 'Sign in' }).click(),
  ])
}

test('renders login instead of an empty shell before authentication', async ({ page }) => {
  const failedAssets: string[] = []
  page.on('response', (response) => {
    const url = response.url()
    if ((url.includes('/assets/') || url.endsWith('/index.html')) && response.status() >= 400) {
      failedAssets.push(`${response.status()} ${url}`)
    }
  })

  await page.goto(appPath('/'))

  await expect(page.locator('#root')).not.toBeEmpty()
  await expect(page.getByRole('heading', { name: 'Login' })).toBeVisible()
  expect(failedAssets).toEqual([])
})

test('renders main wiki shell instead of a white screen', async ({ page }) => {
  await signIn(page)

  await expect(page.locator('#root')).not.toBeEmpty()
  await expect(page.getByRole('heading', { name: 'GolemCore Brain' })).toBeVisible()
  await expect(page.getByRole('banner').getByRole('button', { name: 'Search' })).toBeVisible()
  await expect(page.getByTestId('sidebar')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Tree' })).toBeVisible()

  const bodyText = await page.locator('body').innerText()
  expect(bodyText.trim().length).toBeGreaterThan(20)
})

test('navigates to a seeded page and opens the editor', async ({ page }) => {
  await signIn(page)

  await page.goto(appPath('/product'))
  await expect(page.getByRole('heading', { level: 1, name: 'Product' })).toBeVisible()

  await page.goto(appPath('/e/product/roadmap'))
  await expect(page.getByRole('button', { name: 'Close editor' })).toBeVisible()
  await expect(page.locator('.cm-editor')).toBeVisible()
})
