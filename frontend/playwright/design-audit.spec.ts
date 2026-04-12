import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

const viewports = [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'mobile', width: 390, height: 844 },
]

async function expectDarkShell(page: Page) {
  await expect(page.locator('html')).toHaveClass(/dark/)
  const colors = await page.evaluate(() => {
    const luminance = (value: string | null) => {
      const match = /rgba?\((\d+),\s*(\d+),\s*(\d+)/.exec(value ?? '')
      if (!match) {
        return 0
      }
      const red = Number(match[1])
      const green = Number(match[2])
      const blue = Number(match[3])
      return (0.2126 * red + 0.7152 * green + 0.0722 * blue) / 255
    }
    const backgroundFor = (selector: string) => {
      const element = document.querySelector(selector)
      if (!element) {
        return null
      }
      return getComputedStyle(element).backgroundColor
    }
    return {
      body: luminance(backgroundFor('body')),
      header: luminance(backgroundFor('header')),
      sidebar: luminance(backgroundFor('#sidebar')),
    }
  })
  expect(colors.body).toBeLessThan(0.25)
  if (colors.header > 0) {
    expect(colors.header).toBeLessThan(0.3)
  }
  if (colors.sidebar > 0) {
    expect(colors.sidebar).toBeLessThan(0.25)
  }
}

async function openHome(page: Page, screenshotName: string) {
  await page.goto('/login')
  await page.waitForLoadState('networkidle')
  await expectDarkShell(page)

  const loginHeading = page.getByRole('heading', { name: 'Login' })
  if (await loginHeading.isVisible().catch(() => false)) {
    await page.screenshot({ path: screenshotName.replace('home', 'login'), fullPage: true })
    await page.getByLabel('Username or email').fill('admin')
    await page.getByLabel('Password').fill('admin')
    await Promise.all([
      page.waitForURL('**/'),
      page.getByRole('button', { name: 'Sign in' }).click(),
    ])
  } else {
    await page.goto('/')
  }

  await page.waitForLoadState('networkidle')
  await expect(page.locator('#root')).not.toBeEmpty()
  await expectDarkShell(page)
  await page.screenshot({ path: screenshotName, fullPage: true })
}

test.describe('Design audit', () => {
  test.beforeEach(async ({ context }) => {
    await context.clearCookies()
  })

  for (const viewport of viewports) {
    test(`captures key screens at ${viewport.name}`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height })
      await openHome(page, `audit-${viewport.name}-01-home.png`)

      const goto = async (path: string, filename: string) => {
        await page.goto(path)
        await page.waitForLoadState('networkidle')
        await expectDarkShell(page)
        await page.screenshot({ path: filename, fullPage: true })
      }

      await goto('/product', `audit-${viewport.name}-02-product.png`)
      await goto('/product/roadmap', `audit-${viewport.name}-03-product-roadmap.png`)
      await goto('/e/product/roadmap', `audit-${viewport.name}-04-editor.png`)
      await goto('/account', `audit-${viewport.name}-05-account.png`)
      await goto('/users', `audit-${viewport.name}-06-users.png`)
      await goto('/import', `audit-${viewport.name}-07-import.png`)
    })
  }
})
