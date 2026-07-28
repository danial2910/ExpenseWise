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

async function addExpense(page: Page, amount: string, category: string, description: string) {
  await page.getByTestId('nav-transactions').click()
  await expect(page).toHaveURL(/\/transactions$/)
  await page.getByTestId('add-transaction-button').click()
  await page.getByTestId('transaction-amount-input').fill(amount)
  await page.locator('[data-testid=transaction-date-input]').fill(isoDate(new Date()))
  await page.getByTestId('transaction-category-select').click()
  await page.getByRole('option', { name: category, exact: true }).click()
  await page.getByTestId('transaction-description-input').fill(description)
  await page.getByTestId('transaction-save-button').click()
  await expect(page.getByTestId('transaction-editor-dialog')).toBeHidden()
}

test.describe('Budgets', () => {
  test('a user can set an overall and category budget, watch spending track against them, then edit and delete a budget', async ({ page }) => {
    await registerAndLand(page, 'Priya Budgets', 'priya.budgets')
    await page.getByTestId('nav-budgets').click()
    await expect(page).toHaveURL(/\/budgets$/)

    await expect(page.getByTestId('budgets-empty-state')).toBeVisible()

    // Set the overall monthly budget.
    await page.getByTestId('set-overall-budget-button').click()
    await page.getByTestId('budget-amount-input').fill('500')
    await page.getByTestId('budget-save-button').click()
    await expect(page.getByTestId('budget-editor-dialog')).toBeHidden()
    await expect(page.getByTestId('overall-budget-card')).toContainText('500.00')

    // Set a category budget for Food.
    const foodRow = page.locator('[data-testid^="budget-row-"]', { hasText: 'Food' })
    await foodRow.getByTestId(/^budget-set-button-/).click()
    await page.getByTestId('budget-amount-input').fill('50')
    await page.getByTestId('budget-save-button').click()
    await expect(page.getByTestId('budget-editor-dialog')).toBeHidden()
    await expect(foodRow).toContainText('50.00')

    // Spend under the category limit — 20/50 = 40%, on track.
    await addExpense(page, '20.00', 'Food', 'Groceries')
    await page.getByTestId('nav-budgets').click()
    await expect(foodRow).toContainText('20.00')
    await expect(foodRow).toContainText('40%')
    await expect(page.getByTestId('overall-budget-card')).toContainText('On track')

    // Spend past the category limit — 60/50 = 120%, over budget. The
    // overall budget (60/500) is still on track, proving the two limits
    // are scored independently rather than one rolling up into the other.
    await addExpense(page, '40.00', 'Food', 'More groceries')
    await page.getByTestId('nav-budgets').click()
    await expect(foodRow).toContainText('120%')
    await expect(page.getByTestId('overall-budget-card')).toContainText('On track')

    // Edit the category budget's limit — 60/100 = 60%, back on track.
    await foodRow.getByTestId(/^budget-edit-button-/).click()
    await page.getByTestId('budget-amount-input').fill('100')
    await page.getByTestId('budget-save-button').click()
    await expect(page.getByTestId('budget-editor-dialog')).toBeHidden()
    await expect(foodRow).toContainText('100.00')
    await expect(foodRow).toContainText('60%')

    // Category budgets are capped against the overall budget: Food already
    // takes 100 of the 500 overall, so a Transport budget of 450 would push
    // the total over — the save is rejected with an inline error and the
    // dialog stays open.
    const transportRow = page.locator('[data-testid^="budget-row-"]', { hasText: 'Transport' })
    await transportRow.getByTestId(/^budget-set-button-/).click()
    await page.getByTestId('budget-amount-input').fill('450')
    await page.getByTestId('budget-save-button').click()
    await expect(page.getByText('Category budgets cannot exceed the overall monthly budget')).toBeVisible()
    await expect(page.getByTestId('budget-editor-dialog')).toBeVisible()

    // A Transport budget that fits within the remaining headroom (400) succeeds.
    await page.getByTestId('budget-amount-input').fill('200')
    await page.getByTestId('budget-save-button').click()
    await expect(page.getByTestId('budget-editor-dialog')).toBeHidden()
    await expect(transportRow).toContainText('200.00')

    // The overall budget can't be lowered below what's already allocated to
    // categories (100 Food + 200 Transport = 300).
    await page.getByTestId('overall-budget-edit-button').click()
    await page.getByTestId('budget-amount-input').fill('250')
    await page.getByTestId('budget-save-button').click()
    await expect(page.getByText('Overall budget cannot be less than the sum of category budgets already set')).toBeVisible()
    await page.getByTestId('budget-cancel-button').click()

    // Clearing the overall budget is blocked while category budgets exist.
    await expect(page.getByTestId('overall-budget-clear-button')).toBeDisabled()

    // Delete (clear) the category budgets — rows fall back to "no budget set".
    await foodRow.getByTestId(/^budget-clear-button-/).click()
    await expect(foodRow).toContainText('No budget set')
    await transportRow.getByTestId(/^budget-clear-button-/).click()
    await expect(transportRow).toContainText('No budget set')

    // With no category budgets left, the overall budget can be cleared too.
    await expect(page.getByTestId('overall-budget-clear-button')).toBeEnabled()
    await page.getByTestId('overall-budget-clear-button').click()
    await expect(page.getByTestId('budgets-empty-state')).toBeVisible()
  })
})
