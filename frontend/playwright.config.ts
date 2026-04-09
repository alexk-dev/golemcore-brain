import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './playwright',
  timeout: 30_000,
  use: {
    browserName: 'chromium',
    headless: true,
    baseURL: 'http://127.0.0.1:18082',
  },
})
