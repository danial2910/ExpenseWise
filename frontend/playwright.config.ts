import { defineConfig, devices } from '@playwright/test'

// Assumes the backend (mvn spring-boot:run) and local Docker Postgres are
// already running per the project's documented dev commands — this config
// only manages the frontend dev server. Never point this at Supabase.
export default defineConfig({
  testDir: './e2e',
  // fullyParallel: false only disables intra-file parallelism (tests within
  // one spec still run sequentially); Playwright still schedules different
  // spec FILES across worker processes in parallel by default. This suite
  // runs every spec against one shared, stateful backend + one shared local
  // Postgres (docker-compose, :5433) — not an isolated backend per worker —
  // so concurrent specs contend over shared state: the login rate limiter
  // (keyed per-IP, and every worker hits it from localhost), DB rows a spec
  // assumes are exclusively its own, and in-memory caches. workers: 1
  // serializes the whole run to match how the tests already pass when run
  // individually. E2E isn't part of the Jenkins pipeline, so the extra
  // runtime this costs is an acceptable trade for a suite that's actually
  // green end to end.
  workers: 1,
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
