import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'

function uniqueEmail(prefix: string): string {
  return `${prefix}+${Date.now()}-${Math.floor(Math.random() * 10000)}@example.com`
}

async function registerAndLand(page: Page, fullName: string, prefix: string) {
  const email = uniqueEmail(prefix)
  const password = 'Passw0rd1'

  await page.goto('/register')
  await page.getByTestId('register-fullname-input').fill(fullName)
  await page.getByTestId('register-email-input').fill(email)
  await page.locator('[data-testid=register-password-input] input').fill(password)
  await page.locator('[data-testid=register-confirm-password-input] input').fill(password)
  await page.getByTestId('register-submit-button').click()
  await expect(page).toHaveURL(/\/dashboard$/)
}

// A real NEWSDATA_API_KEY_EXPENSEWISE is set on this dev machine, so this
// journey hits the real NewsData.io API through the backend — same
// precedent as the AI Assistant module's E2E test. Never assert on
// specific article content (it's non-deterministic, real-world news); only
// that cards actually render.
const NEWS_LOAD_TIMEOUT = 15000

test.describe('News', () => {
  test('a user with News enabled can open the News page and see article cards render', async ({ page }) => {
    await registerAndLand(page, 'Nadia News', 'nadia.news')

    await page.getByTestId('nav-news').click()
    await expect(page).toHaveURL(/\/news$/)

    await expect(page.getByTestId('news-loading-skeleton').or(page.getByTestId('news-articles-grid'))).toBeVisible()
    await expect(page.getByTestId('news-article-card-0')).toBeVisible({ timeout: NEWS_LOAD_TIMEOUT })
    await expect(page.getByTestId('news-article-title-0')).not.toBeEmpty()
    await expect(page.getByTestId('news-article-readmore-0')).toHaveAttribute('target', '_blank')
    await expect(page.getByTestId('news-error-state')).toBeHidden()
  })
})
