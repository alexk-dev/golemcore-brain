import { defineConfig } from '@playwright/test'

const standaloneBaseUrl = 'http://127.0.0.1:5273'

export default defineConfig({
  testDir: './playwright',
  timeout: 30_000,
  workers: 1,
  use: {
    browserName: 'chromium',
    headless: true,
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? standaloneBaseUrl,
  },
  webServer: process.env.PLAYWRIGHT_BASE_URL
    ? undefined
    : {
        command: 'npm run dev -- --host 127.0.0.1 --port 5273 --strictPort',
        url: standaloneBaseUrl,
        reuseExistingServer: true,
        timeout: 60_000,
      },
})
