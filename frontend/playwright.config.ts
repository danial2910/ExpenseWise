import { defineConfig, devices } from '@playwright/test'

// Assumes the backend (mvn spring-boot:run) and local Docker Postgres are
// already running per the project's documented dev commands — this config
// only manages the frontend dev server. Never point this at Supabase.
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  // channel: 'chrome' uses the system-installed Google Chrome instead of
  // Playwright's bundled "Chrome for Testing" build, which Windows Smart App
  // Control blocks (unsigned chrome_elf.dll). Chrome is properly signed.
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], channel: 'chrome' } }],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 30000,
  },
})
