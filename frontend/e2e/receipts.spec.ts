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

// A minimal valid 1x1 JPEG, just enough bytes for the browser/multipart
// upload to treat it as a real file — content itself is never asserted on.
const FAKE_JPEG = Buffer.from(
  '/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAMCAgICAgMCAgIDAwMDBAYEBAQEBAgGBgUGCQgKCgkICQkKDA8MCgsOCwkJDRENDg8QEBEQCgwSExIQEw8QEBD/2wBDAQMDAwQDBAgEBAgQCwkLEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBD/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAj/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCdABmX/9k=',
  'base64',
)

test.describe('Receipts', () => {
  test('a user can attach a receipt to a transaction and remove it', async ({ page }) => {
    await registerAndLand(page, 'Priya Receipts', 'priya.receipts')
    await page.getByTestId('nav-transactions').click()
    await expect(page).toHaveURL(/\/transactions$/)

    // Add a transaction — receipts can only be attached once it has an id.
    await page.getByTestId('add-transaction-button').click()
    await page.getByTestId('transaction-amount-input').fill('32.50')
    await page.locator('[data-testid=transaction-date-input]').fill(isoDate(new Date()))
    await page.getByTestId('transaction-category-select').click()
    await page.getByRole('option', { name: 'Food', exact: true }).click()
    await page.getByTestId('transaction-description-input').fill('Grocery run')
    await expect(page.getByTestId('receipt-unavailable-hint')).toBeVisible()
    await page.getByTestId('transaction-save-button').click()
    await expect(page.getByTestId('transaction-editor-dialog')).toBeHidden()

    const row = page.locator('[data-testid^="transaction-row-"]', { hasText: 'Grocery run' })
    await expect(row).toBeVisible()
    await expect(row.locator('[data-testid^="transaction-receipt-link-"]')).toHaveCount(0)

    // Reopen for editing and upload a receipt.
    await row.getByTestId(/^transaction-edit-button-/).click()
    await expect(page.getByTestId('receipt-upload-button')).toBeVisible()
    await page.getByTestId('receipt-upload-input').setInputFiles({
      name: 'receipt.jpg',
      mimeType: 'image/jpeg',
      buffer: FAKE_JPEG,
    })
    await expect(page.getByTestId('receipt-view-link')).toBeVisible()
    await page.getByTestId('transaction-cancel-button').click()

    // The row now shows a receipt indicator linking to the signed URL.
    await expect(row.getByTestId(/^transaction-receipt-link-/)).toBeVisible()

    // Remove it via the editor.
    await row.getByTestId(/^transaction-edit-button-/).click()
    await expect(page.getByTestId('receipt-view-link')).toBeVisible()
    await page.getByTestId('receipt-remove-button').click()
    await expect(page.getByTestId('receipt-upload-button')).toBeVisible()
    await page.getByTestId('transaction-cancel-button').click()

    await expect(row.locator('[data-testid^="transaction-receipt-link-"]')).toHaveCount(0)
  })
})
