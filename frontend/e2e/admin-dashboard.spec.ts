import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import { promoteToAdmin } from './db'

function uniqueEmail(prefix: string): string {
  return `${prefix}+${Date.now()}-${Math.floor(Math.random() * 10000)}@example.com`
}

async function registerAndLand(page: Page, fullName: string, prefix: string): Promise<string> {
  const email = uniqueEmail(prefix)
  const password = 'Passw0rd1'

  await page.goto('/register')
  await page.getByTestId('register-fullname-input').fill(fullName)
  await page.getByTestId('register-email-input').fill(email)
  await page.locator('[data-testid=register-password-input] input').fill(password)
  await page.locator('[data-testid=register-confirm-password-input] input').fill(password)
  await page.getByTestId('register-submit-button').click()
  await expect(page).toHaveURL(/\/dashboard$/)
  return email
}

test.describe('Admin dashboard', () => {
  test('an admin can open the Admin Dashboard and see the summary cards and charts render', async ({ page, browser }) => {
    // Seed a couple of regular users first, each in their OWN browser
    // context — register logs the user straight in, so reusing one page (or
    // even one context) for a second registration would still be carrying
    // the first seed user's httpOnly refresh cookie and get redirected away
    // from /register by the guestOnly guard before the form ever renders.
    // A fresh browser.newContext() per user is a clean cookie jar, and using
    // separate contexts (not just tabs) also keeps this from affecting the
    // admin session set up below on the original `page`.
    for (const [name, prefix] of [
      ['Seed One', 'admindash.seed1'],
      ['Seed Two', 'admindash.seed2'],
    ] as const) {
      const seedContext = await browser.newContext()
      const seedPage = await seedContext.newPage()
      await registerAndLand(seedPage, name, prefix)
      await seedContext.close()
    }

    const adminEmail = await registerAndLand(page, 'Priya Admin', 'admindash.admin')
    await promoteToAdmin(adminEmail)

    // The token issued at registration still carries role=USER; reloading
    // triggers the auth store's bootstrap() -> refresh flow, which reissues
    // an access token from the (now updated) role in the database.
    await page.reload()
    await expect(page).toHaveURL(/\/admin\/dashboard$/)

    await expect(page.getByTestId('admin-dashboard-content')).toBeVisible()
    await expect(page.getByTestId('summary-total-users')).toBeVisible()
    await expect(page.getByTestId('summary-active-disabled')).toBeVisible()
    await expect(page.getByTestId('summary-new-this-month')).toBeVisible()

    await expect(page.getByTestId('signups-chart').locator('canvas')).toBeVisible()
    await expect(page.getByTestId('activity-chart').locator('canvas')).toBeVisible()

    await expect(page.getByTestId('feature-usage-TRANSACTIONS')).toBeVisible()
    await expect(page.getByTestId('feature-usage-AI_ASSISTANT')).toBeVisible()

    await expect(page.getByTestId('recent-signups-list')).toBeVisible()
  })
})
