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

test.describe('Profile', () => {
  test('a user can update their profile fields, see them persist on reload, then change their password', async ({ page }) => {
    const email = await registerAndLand(page, 'Priya Profile', 'priya.profile')

    await page.getByTestId('nav-profile').click()
    await expect(page).toHaveURL(/\/profile$/)
    await expect(page.getByTestId('profile-tab-personal')).toBeVisible()

    await page.getByTestId('profile-phone-input').fill('+60 12-345 6789')
    await page.getByTestId('profile-address-textarea').fill('1 Jalan Test, Kuala Lumpur')
    await page.getByTestId('profile-save-button').click()

    await expect(page.locator('text=Profile updated.')).toBeVisible()

    await page.reload()
    await expect(page.getByTestId('profile-phone-input')).toHaveValue('+60 12-345 6789')
    await expect(page.getByTestId('profile-address-textarea')).toHaveValue('1 Jalan Test, Kuala Lumpur')

    await page.getByTestId('profile-tab-security').click()
    await page.getByTestId('change-password-current-input').fill('Passw0rd1')
    await page.getByTestId('change-password-new-input').fill('NewPassw0rd1')
    await page.getByTestId('change-password-confirm-input').fill('NewPassw0rd1')
    await page.getByTestId('change-password-submit-button').click()

    await expect(page.locator('text=Password changed. Other sessions have been signed out.')).toBeVisible()

    await page.getByTestId('logout-button').click()
    await expect(page).toHaveURL(/\/login$/)

    await page.getByTestId('login-email-input').fill(email)
    await page.locator('[data-testid=login-password-input] input').fill('NewPassw0rd1')
    await page.getByTestId('login-submit-button').click()
    await expect(page).toHaveURL(/\/dashboard$/)
  })
})
