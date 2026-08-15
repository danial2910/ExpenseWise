import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'

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

test.describe('About', () => {
  // Single required journey per CLAUDE.md: open About, see the app version,
  // and open/close each of the three static info dialogs.
  test('a user can open About, see the app version, and open Terms/Privacy/Help', async ({ page }) => {
    await registerAndLand(page, 'Ali About', 'ali.about')

    await page.getByTestId('nav-about').click()
    await expect(page).toHaveURL(/\/about$/)

    await expect(page.getByTestId('about-app-version')).toBeVisible()
    await expect(page.getByTestId('about-app-version')).toHaveText(/^v\d+\.\d+\.\d+$/)

    await page.getByTestId('about-terms-link').click()
    await expect(page.getByTestId('about-terms-dialog')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('about-terms-dialog')).not.toBeVisible()

    await page.getByTestId('about-privacy-link').click()
    await expect(page.getByTestId('about-privacy-dialog')).toBeVisible()
    await page.keyboard.press('Escape')

    await page.getByTestId('about-help-link').click()
    await expect(page.getByTestId('about-help-dialog')).toBeVisible()
  })
})
