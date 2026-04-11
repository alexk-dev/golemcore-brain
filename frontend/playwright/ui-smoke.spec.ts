import { expect, test } from '@playwright/test'

test.skip(
  !process.env.PLAYWRIGHT_BASE_URL,
  'Requires a running backend; set PLAYWRIGHT_BASE_URL to the brain app URL.',
)

async function signIn(page: import('@playwright/test').Page) {
  await page.goto('/login')
  await page.getByLabel('Username or email').fill('admin')
  await page.getByLabel('Password').fill('admin')
  await Promise.all([
    page.waitForURL('**/'),
    page.getByRole('button', { name: 'Sign in' }).click(),
  ])
}

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

  await page.goto('/product')
  await expect(page.getByRole('heading', { level: 1, name: 'Product' })).toBeVisible()

  await page.goto('/e/product/roadmap')
  await expect(page.getByRole('button', { name: 'Close editor' })).toBeVisible()
  await expect(page.locator('.cm-editor')).toBeVisible()
})
