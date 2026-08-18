import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'

function uniqueEmail(prefix: string): string {
  return `${prefix}+${Date.now()}-${Math.floor(Math.random() * 10000)}@example.com`
}

async function registerAndLand(page: Page, fullName: string, prefix: string): Promise<void> {
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

test.describe('Landing', () => {
  test('a guest sees the landing page at / and its CTAs reach register/login; a signed-in visitor is redirected to their dashboard', async ({ page }) => {
    // Guest: / renders the landing page itself, not a redirect.
    await page.goto('/')
    await expect(page).toHaveURL('/')
    await expect(page.getByTestId('landing-hero-get-started-button')).toBeVisible()

    // Primary CTA goes to registration.
    await page.getByTestId('landing-hero-get-started-button').click()
    await expect(page).toHaveURL(/\/register$/)

    // Secondary nav link goes to sign-in.
    await page.goto('/')
    await page.getByTestId('landing-nav-signin-link').click()
    await expect(page).toHaveURL(/\/login$/)

    // Footer legal dialogs open without navigating away.
    await page.goto('/')
    await page.getByTestId('landing-footer-terms-link').click()
    await expect(page.getByTestId('landing-terms-dialog')).toBeVisible()
    await page.keyboard.press('Escape')
    await page.getByTestId('landing-footer-privacy-link').click()
    await expect(page.getByTestId('landing-privacy-dialog')).toBeVisible()
    await page.keyboard.press('Escape')

    // An authenticated visitor hitting / is redirected to their dashboard,
    // not shown the landing page.
    await registerAndLand(page, 'Landing Visitor', 'landing.visitor')
    await page.goto('/')
    await expect(page).toHaveURL(/\/dashboard$/)
  })
})
