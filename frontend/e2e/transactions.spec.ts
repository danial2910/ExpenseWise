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
  date?: string
  category: string
  description: string
}

async function addTransaction(page: Page, input: TransactionInput) {
  await page.getByTestId('add-transaction-button').click()
  if (input.type === 'Income') {
    await page.getByTestId('transaction-type-toggle').getByText('Income').click()
  }
  await page.getByTestId('transaction-amount-input').fill(input.amount)
  await page.locator('[data-testid=transaction-date-input]').fill(input.date ?? isoDate(new Date()))
  await page.getByTestId('transaction-category-select').click()
  await page.getByRole('option', { name: input.category, exact: true }).click()
  await page.getByTestId('transaction-description-input').fill(input.description)
  await page.getByTestId('transaction-save-button').click()
  await expect(page.getByTestId('transaction-editor-dialog')).toBeHidden()
}

test.describe('Transactions', () => {
  test('a user can record income and expense, see the balance update, edit one, and delete it', async ({ page }) => {
    await registerAndLand(page, 'Priya Transactions', 'priya.transactions')
    await page.getByTestId('nav-transactions').click()
    await expect(page).toHaveURL(/\/transactions$/)

    await addTransaction(page, { type: 'Expense', amount: '50.00', category: 'Food', description: 'Groceries run' })
    await expect(page.getByTestId('transactions-table')).toContainText('Groceries run')

    await addTransaction(page, { type: 'Income', amount: '2000.00', category: 'Salary', description: 'Payday' })
    await expect(page.getByTestId('transactions-table')).toContainText('Payday')

    // Balance reflects both rows: 2000.00 income - 50.00 expense = 1950.00.
    await expect(page.getByTestId('summary-balance')).toContainText('1,950.00')

    // Edit the expense's description.
    const expenseRow = page.locator('[data-testid^="transaction-row-"]', { hasText: 'Groceries run' })
    await expenseRow.locator('[data-testid^="transaction-edit-button-"]').click()
    await page.getByTestId('transaction-description-input').fill('Groceries run edited')
    await page.getByTestId('transaction-save-button').click()
    await expect(page.getByTestId('transaction-editor-dialog')).toBeHidden()
    await expect(page.getByTestId('transactions-table')).toContainText('Groceries run edited')

    // Delete the income row and confirm the balance drops back to -50.00.
    const incomeRow = page.locator('[data-testid^="transaction-row-"]', { hasText: 'Payday' })
    await incomeRow.locator('[data-testid^="transaction-delete-button-"]').click()
    await expect(page.getByTestId('transactions-table')).not.toContainText('Payday')
    await expect(page.getByTestId('summary-balance')).toContainText('50.00')
  })

  test('a brand new user sees the empty state before adding any transactions', async ({ page }) => {
    await registerAndLand(page, 'Priya Empty', 'priya.empty')
    await page.getByTestId('nav-transactions').click()

    await expect(page.getByTestId('transactions-empty-state')).toBeVisible()
    await expect(page.getByTestId('summary-total-income')).toContainText('0.00')
    await expect(page.getByTestId('summary-total-expense')).toContainText('0.00')
  })

  test('the summary totals include transactions from any date, not just the current month', async ({ page }) => {
    // Regression test: the summary endpoint previously defaulted to "this
    // calendar month" when no date filter was applied, so a transaction
    // dated last month wouldn't be counted even though it was visible in
    // the unfiltered list right below it. See DECISIONS.md, 2026-07-27.
    await registerAndLand(page, 'Priya OldDate', 'priya.olddate')
    await page.getByTestId('nav-transactions').click()

    const lastMonth = new Date()
    lastMonth.setMonth(lastMonth.getMonth() - 1)

    await addTransaction(page, {
      type: 'Income',
      amount: '800.00',
      date: isoDate(lastMonth),
      category: 'Allowance',
      description: 'Elaun monthly mara',
    })
    await addTransaction(page, {
      type: 'Expense',
      amount: '20.00',
      date: isoDate(lastMonth),
      category: 'Bills',
      description: 'Hutang dinner FOC',
    })

    await expect(page.getByTestId('transactions-table')).toContainText('Elaun monthly mara')
    await expect(page.getByTestId('summary-total-income')).toContainText('800.00')
    await expect(page.getByTestId('summary-total-expense')).toContainText('20.00')
    await expect(page.getByTestId('summary-balance')).toContainText('780.00')
  })

  test('filtering by type shows only income or only expense rows', async ({ page }) => {
    await registerAndLand(page, 'Priya TypeFilter', 'priya.typefilter')
    await page.getByTestId('nav-transactions').click()

    await addTransaction(page, { type: 'Expense', amount: '15.00', category: 'Food', description: 'Lunch' })
    await addTransaction(page, { type: 'Income', amount: '500.00', category: 'Freelance', description: 'Gig payout' })

    await page.getByTestId('transaction-type-filter').getByText('Income').click()
    await expect(page.getByTestId('transactions-table')).toContainText('Gig payout')
    await expect(page.getByTestId('transactions-table')).not.toContainText('Lunch')

    await page.getByTestId('transaction-type-filter').getByText('Expense').click()
    await expect(page.getByTestId('transactions-table')).toContainText('Lunch')
    await expect(page.getByTestId('transactions-table')).not.toContainText('Gig payout')
  })

  test('filtering by category shows only transactions in that category', async ({ page }) => {
    await registerAndLand(page, 'Priya CategoryFilter', 'priya.categoryfilter')
    await page.getByTestId('nav-transactions').click()

    await addTransaction(page, { type: 'Expense', amount: '10.00', category: 'Food', description: 'Snacks' })
    await addTransaction(page, { type: 'Expense', amount: '30.00', category: 'Transport', description: 'Petrol' })

    await page.getByTestId('transaction-category-filter').click()
    await page.getByRole('option', { name: 'Food', exact: true }).click()

    await expect(page.getByTestId('transactions-table')).toContainText('Snacks')
    await expect(page.getByTestId('transactions-table')).not.toContainText('Petrol')
  })

  test('searching by description filters the list', async ({ page }) => {
    await registerAndLand(page, 'Priya Search', 'priya.search')
    await page.getByTestId('nav-transactions').click()

    await addTransaction(page, { type: 'Expense', amount: '12.00', category: 'Food', description: 'Nasi lemak breakfast' })
    await addTransaction(page, { type: 'Expense', amount: '45.00', category: 'Shopping', description: 'New shoes' })

    await page.getByTestId('transaction-search-input').fill('nasi lemak')
    await expect(page.getByTestId('transactions-table')).toContainText('Nasi lemak breakfast')
    await expect(page.getByTestId('transactions-table')).not.toContainText('New shoes')
  })

  test('filtering by date range excludes transactions outside it', async ({ page }) => {
    await registerAndLand(page, 'Priya DateFilter', 'priya.datefilter')
    await page.getByTestId('nav-transactions').click()

    const today = new Date()
    const lastMonth = new Date()
    lastMonth.setMonth(lastMonth.getMonth() - 1)

    await addTransaction(page, {
      type: 'Expense',
      amount: '18.00',
      date: isoDate(today),
      category: 'Food',
      description: 'Todays coffee',
    })
    await addTransaction(page, {
      type: 'Expense',
      amount: '99.00',
      date: isoDate(lastMonth),
      category: 'Bills',
      description: 'Last months bill',
    })

    await page.locator('[data-testid=transaction-date-from-input]').fill(isoDate(today))
    await expect(page.getByTestId('transactions-table')).toContainText('Todays coffee')
    await expect(page.getByTestId('transactions-table')).not.toContainText('Last months bill')
  })

  test('an amount of zero or blank is rejected with an inline error and does not submit', async ({ page }) => {
    await registerAndLand(page, 'Priya Validation', 'priya.validation')
    await page.getByTestId('nav-transactions').click()

    await page.getByTestId('add-transaction-button').click()
    await page.getByTestId('transaction-amount-input').fill('0')
    await page.locator('[data-testid=transaction-date-input]').fill(isoDate(new Date()))
    await page.getByTestId('transaction-category-select').click()
    await page.getByRole('option', { name: 'Food', exact: true }).click()
    await page.getByTestId('transaction-save-button').click()

    await expect(page.getByText('Amount must be greater than zero')).toBeVisible()
    await expect(page.getByTestId('transaction-editor-dialog')).toBeVisible()
  })

  test('switching the type toggle clears a category that no longer matches', async ({ page }) => {
    await registerAndLand(page, 'Priya TypeSwitch', 'priya.typeswitch')
    await page.getByTestId('nav-transactions').click()

    await page.getByTestId('add-transaction-button').click()
    await page.getByTestId('transaction-category-select').click()
    await page.getByRole('option', { name: 'Food', exact: true }).click()

    await page.getByTestId('transaction-type-toggle').getByText('Income').click()
    await page.getByTestId('transaction-save-button').click()

    await expect(page.getByText('Category is required')).toBeVisible()
    await expect(page.getByTestId('transaction-editor-dialog')).toBeVisible()
  })

  test('reset filters clears every active filter and shows the full list again', async ({ page }) => {
    await registerAndLand(page, 'Priya Reset', 'priya.reset')
    await page.getByTestId('nav-transactions').click()

    await addTransaction(page, { type: 'Expense', amount: '10.00', category: 'Food', description: 'Snacks' })
    await addTransaction(page, { type: 'Income', amount: '500.00', category: 'Freelance', description: 'Gig payout' })

    const resetButton = page.getByTestId('transaction-reset-filters-button')
    await expect(resetButton).toBeDisabled()

    // Narrow the list down with several filters at once.
    await page.getByTestId('transaction-type-filter').getByText('Expense').click()
    await page.getByTestId('transaction-search-input').fill('Snacks')
    await expect(page.getByTestId('transactions-table')).toContainText('Snacks')
    await expect(page.getByTestId('transactions-table')).not.toContainText('Gig payout')
    await expect(resetButton).toBeEnabled()

    await resetButton.click()

    await expect(page.getByTestId('transaction-search-input')).toHaveValue('')
    await expect(page.getByTestId('transactions-table')).toContainText('Snacks')
    await expect(page.getByTestId('transactions-table')).toContainText('Gig payout')
    await expect(resetButton).toBeDisabled()
  })
})
