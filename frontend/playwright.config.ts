import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  timeout: 45000,
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:15173',
    browserName: 'chromium',
    channel: process.env.PLAYWRIGHT_CHANNEL || undefined,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: process.env.E2E_BASE_URL ? undefined : {
    command: 'npm run dev -- --host 127.0.0.1 --port 15173 --strictPort',
    url: 'http://127.0.0.1:15173',
    reuseExistingServer: !process.env.CI,
    env: { VITE_API_PROXY_TARGET: 'http://127.0.0.1:18080' },
  },
})
