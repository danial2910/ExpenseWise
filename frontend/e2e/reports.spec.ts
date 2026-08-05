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

test.describe('Reports', () => {
  test('a user with data can generate a monthly report and download it as PDF and Excel', async ({ page }) => {
    await registerAndLand(page, 'Priya Reports', 'priya.reports')

    await page.getByTestId('nav-transactions').click()
    await expect(page).toHaveURL(/\/transactions$/)
    await addTransaction(page, { type: 'Income', amount: '2500.00', category: 'Salary', description: 'Payday' })
    await addTransaction(page, { type: 'Expense', amount: '80.00', category: 'Food', description: 'Groceries run' })

    await page.getByTestId('nav-reports').click()
    await expect(page).toHaveURL(/\/reports$/)
    await expect(page.getByTestId('reports-content')).toBeVisible()

    await expect(page.getByTestId('summary-income')).toContainText('2,500.00')
    await expect(page.getByTestId('summary-expense')).toContainText('80.00')
    await expect(page.getByTestId('report-category-breakdown')).toContainText('Food')
    await expect(page.getByTestId('report-trend-chart').locator('canvas')).toBeVisible()

    const pdfDownload = page.waitForEvent('download')
    await page.getByTestId('report-export-pdf-button').click()
    const pdf = await pdfDownload
    expect(pdf.suggestedFilename()).toMatch(/\.pdf$/)

    const excelDownload = page.waitForEvent('download')
    await page.getByTestId('report-export-excel-button').click()
    const excel = await excelDownload
    expect(excel.suggestedFilename()).toMatch(/\.xlsx$/)
  })
})
