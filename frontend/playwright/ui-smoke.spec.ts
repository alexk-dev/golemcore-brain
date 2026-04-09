import { expect, test } from '@playwright/test'

test('renders main wiki shell instead of a white screen', async ({ page }) => {
  await page.goto('/')

  await expect(page.locator('#root')).not.toBeEmpty()
  await expect(page.getByText('GolemCore Brain')).toBeVisible()
  await expect(page.getByRole('banner').getByRole('button', { name: 'Search' })).toBeVisible()
  await expect(page.getByTestId('sidebar')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Tree' })).toBeVisible()

  await page.screenshot({ path: 'playwright-smoke-home.png', fullPage: true })

  const bodyText = await page.locator('body').innerText()
  expect(bodyText.trim().length).toBeGreaterThan(20)
})

test('navigates to a seeded page and opens the editor', async ({ page }) => {
  await page.goto('/guides')
  await expect(page.getByText('Guides')).toBeVisible()

  await page.goto('/e/guides/writing-notes')
  await expect(page.getByText('Close editor')).toBeVisible()
  await expect(page.locator('.cm-editor')).toBeVisible()

  await page.screenshot({ path: 'playwright-smoke-editor.png', fullPage: true })
})
