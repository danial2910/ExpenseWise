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

function isoDate(date: Date): string {
  return date.toISOString().slice(0, 10)
}

function shortDate(date: Date): string {
  return date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })
}

test.describe('Recurring', () => {
  test('a user can create a recurring rule due today, generate it now, and see the transaction and advanced schedule', async ({ page }) => {
    await registerAndLand(page, 'Priya Recurring', 'priya.recurring')
    await page.getByTestId('nav-recurring').click()
    await expect(page).toHaveURL(/\/recurring$/)

    await expect(page.getByTestId('recurring-empty-state')).toBeVisible()

    const today = new Date()

    await page.getByTestId('add-recurring-button').click()
    await expect(page.getByTestId('recurring-editor-dialog')).toBeVisible()
    await page.getByTestId('recurring-amount-input').fill('45.00')
    await page.getByTestId('recurring-category-select').click()
    await page.getByRole('option', { name: 'Food', exact: true }).click()
    await page.getByTestId('recurring-description-input').fill('Weekly groceries')
    await page.getByTestId('recurring-frequency-toggle').getByRole('button', { name: 'Weekly', exact: true }).click()
    await page.locator('[data-testid=recurring-start-date-input]').fill(isoDate(today))
    await page.getByTestId('recurring-save-button').click()
    await expect(page.getByTestId('recurring-editor-dialog')).toBeHidden()

    const row = page.locator('[data-testid^="recurring-row-"]', { hasText: 'Weekly groceries' })
    await expect(row).toBeVisible()
    await expect(row).toContainText('Active')
    await expect(row).toContainText(shortDate(today))

    // Run the manual generation trigger — the demo affordance for the
    // auto-post cycle without waiting for the daily scheduler.
    await page.getByTestId('generate-due-button').click()
    await expect(page.getByTestId('generate-due-message')).toContainText('Generated 1 transaction')

    // The next due date advances by one week once generated.
    const nextWeek = new Date(today)
    nextWeek.setDate(nextWeek.getDate() + 7)
    await expect(row).toContainText(shortDate(nextWeek))

    // The generated transaction shows up on the Transactions page.
    await page.getByTestId('nav-transactions').click()
    await expect(page).toHaveURL(/\/transactions$/)
    const transactionRow = page.locator('[data-testid^="transaction-row-"]', { hasText: 'Weekly groceries' })
    await expect(transactionRow).toBeVisible()
    await expect(transactionRow).toContainText('45.00')

    // Back on Recurring, the row's "..." menu is a Popover, not an in-flow
    // dropdown — it must render as a visible overlay even though the table
    // wrapper has overflow-hidden for its rounded corners.
    await page.getByTestId('nav-recurring').click()
    const ruleId = (await row.getAttribute('data-testid'))!.replace('recurring-row-', '')
    await page.getByTestId(`recurring-menu-button-${ruleId}`).click()
    await expect(page.getByTestId(`recurring-pause-button-${ruleId}`)).toBeVisible()

    // Pause via the menu.
    await page.getByTestId(`recurring-pause-button-${ruleId}`).click()
    await expect(row).toContainText('Paused')

    // Resume via the menu.
    await page.getByTestId(`recurring-menu-button-${ruleId}`).click()
    await page.getByTestId(`recurring-pause-button-${ruleId}`).click()
    await expect(row).toContainText('Active')

    // Delete via the menu, with the confirm dialog.
    await page.getByTestId(`recurring-menu-button-${ruleId}`).click()
    await page.getByTestId(`recurring-delete-button-${ruleId}`).click()
    await expect(page.getByTestId('recurring-delete-confirm-dialog')).toBeVisible()
    await page.getByTestId('recurring-delete-confirm-button').click()
    await expect(page.getByTestId('recurring-empty-state')).toBeVisible()
  })
})
