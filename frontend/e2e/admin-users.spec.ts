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

test.describe('Admin user management', () => {
  test('an admin can create a user, disable a feature entitlement, and see the change persist', async ({ page }) => {
    const adminEmail = await registerAndLand(page, 'Priya Admin', 'priya.admin')
    await promoteToAdmin(adminEmail)

    // The token issued at registration still carries role=USER; reloading
    // triggers the auth store's bootstrap() -> refresh flow, which reissues
    // an access token from the (now updated) role in the database.
    await page.reload()
    await expect(page).toHaveURL(/\/admin\/dashboard$/)
    await page.getByTestId('nav-admin-users').click()
    await expect(page).toHaveURL(/\/admin\/users$/)

    const newUserEmail = uniqueEmail('priya.created')
    await page.getByTestId('create-user-button').click()
    await page.getByTestId('user-name-input').fill('Created By Admin')
    await page.getByTestId('user-email-input').fill(newUserEmail)
    await page.getByTestId('user-feature-toggle-BUDGETS').click()
    await page.getByTestId('user-panel-save-button').click()
    // Creating a user sends a real "set your password" email via Brevo's
    // HTTP API synchronously in the request — allow for that network latency.
    await expect(page.getByTestId('user-panel-drawer')).toBeHidden({ timeout: 15000 })

    await page.getByTestId('user-search-input').fill(newUserEmail)
    const row = page.locator('[data-testid^="admin-user-row-"]', { hasText: newUserEmail })
    await expect(row).toBeVisible()

    // Reopen the edit panel and confirm the disabled entitlement persisted.
    await row.locator('[data-testid^="admin-user-edit-"]').click()
    await expect(page.getByTestId('user-panel-drawer')).toBeVisible()
    await expect(page.getByTestId('user-feature-toggle-BUDGETS')).toHaveAttribute('data-p-checked', 'false')
    await expect(page.getByTestId('user-feature-toggle-TRANSACTIONS')).toHaveAttribute('data-p-checked', 'true')

    // Re-enable it, save, and confirm that persists too.
    await page.getByTestId('user-feature-toggle-BUDGETS').click()
    await page.getByTestId('user-panel-save-button').click()
    await expect(page.getByTestId('user-panel-drawer')).toBeHidden()

    await row.locator('[data-testid^="admin-user-edit-"]').click()
    await expect(page.getByTestId('user-feature-toggle-BUDGETS')).toHaveAttribute('data-p-checked', 'true')
  })
})
