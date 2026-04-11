import { expect, test } from '@playwright/test'

const viewports = [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'mobile', width: 390, height: 844 },
]

test.describe('Design audit (admin/admin)', () => {
  test.beforeEach(async ({ context }) => {
    await context.clearCookies()
  })

  for (const viewport of viewports) {
    test(`captures key screens at ${viewport.name}`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height })

      await page.goto('/login')
      await expect(page.getByRole('heading', { name: 'Login' })).toBeVisible()
      await page.screenshot({ path: `audit-${viewport.name}-01-login.png`, fullPage: true })

      await page.getByLabel('Username or email').fill('admin')
      await page.getByLabel('Password').fill('admin')
      await Promise.all([
        page.waitForURL('**/'),
        page.getByRole('button', { name: 'Sign in' }).click(),
      ])
      await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible()
      await page.waitForLoadState('networkidle')
      await page.screenshot({ path: `audit-${viewport.name}-02-home.png`, fullPage: true })

      const goto = async (path: string, filename: string) => {
        await page.goto(path)
        await page.waitForLoadState('networkidle')
        await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible()
        await page.screenshot({ path: filename, fullPage: true })
      }

      await goto('/product', `audit-${viewport.name}-03-product.png`)
      await goto('/product/roadmap', `audit-${viewport.name}-04-product-roadmap.png`)
      await goto('/e/product/roadmap', `audit-${viewport.name}-05-editor.png`)
      await goto('/account', `audit-${viewport.name}-06-account.png`)
      await goto('/users', `audit-${viewport.name}-07-users.png`)
      await goto('/import', `audit-${viewport.name}-08-import.png`)
    })
  }
})
