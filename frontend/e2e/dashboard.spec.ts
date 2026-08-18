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

interface TransactionInput {
  type?: 'Income' | 'Expense'
  amount: string
  category: string
  description: string
}

async function addTransaction(page: Page, input: TransactionInput) {
  await page.getByTestId('add-transaction-button').click()
  if (input.type === 'Income') {
    await page.getByTestId('transaction-type-toggle').getByText('Income').click()
  }
  await page.getByTestId('transaction-amount-input').fill(input.amount)
  await page.locator('[data-testid=transaction-date-input]').fill(isoDate(new Date()))
  await page.getByTestId('transaction-category-select').click()
  await page.getByRole('option', { name: input.category, exact: true }).click()
  await page.getByTestId('transaction-description-input').fill(input.description)
  await page.getByTestId('transaction-save-button').click()
  await expect(page.getByTestId('transaction-editor-dialog')).toBeHidden()
}

test.describe('Dashboard', () => {
  test('a user with transactions sees the summary cards and charts render on the dashboard', async ({ page }) => {
    await registerAndLand(page, 'Priya Dashboard', 'priya.dashboard')

    // A brand new user has no transactions yet — the dashboard degrades to
    // its empty state rather than showing zeroed-out charts.
    await expect(page.getByTestId('dashboard-empty-state')).toBeVisible()

    await page.getByTestId('nav-transactions').click()
    await expect(page).toHaveURL(/\/transactions$/)
    await addTransaction(page, { type: 'Income', amount: '2500.00', category: 'Salary', description: 'Payday' })
    await addTransaction(page, { type: 'Expense', amount: '75.00', category: 'Food', description: 'Groceries run' })

    await page.getByTestId('nav-dashboard').click()
    await expect(page).toHaveURL(/\/dashboard$/)
    await expect(page.getByTestId('dashboard-content')).toBeVisible()

    // Summary cards render the seeded figures (never asserting AI/model
    // content, but these are our own deterministic figures — fine to check).
    await expect(page.getByTestId('summary-income')).toContainText('2,500.00')
    await expect(page.getByTestId('summary-expense')).toContainText('75.00')
    await expect(page.getByTestId('summary-balance')).toContainText('2,425.00')

    // Every chart actually renders a canvas. The old separate "net trend"
    // bar chart was merged into this one Income vs Expense line/area chart
    // (net is fully implied by the two series shown together) — see
    // DECISIONS.md's Phase 3 entry.
    await expect(page.getByTestId('dashboard-income-expense-chart').locator('canvas')).toBeVisible()
    await expect(page.getByTestId('dashboard-category-donut').locator('canvas')).toBeVisible()

    await expect(page.getByTestId('dashboard-budget-categories').or(page.getByText('No category budgets set yet.'))).toBeVisible()
    await expect(page.getByTestId('dashboard-recent-transactions-list')).toContainText('Groceries run')
    await expect(page.getByTestId('dashboard-recent-transactions-list')).toContainText('Payday')
  })
})
