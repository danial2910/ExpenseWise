import { test, expect } from '@playwright/test'

function uniqueEmail(prefix: string): string {
  return `${prefix}+${Date.now()}-${Math.floor(Math.random() * 10000)}@example.com`
}

async function registerAndLand(page: import('@playwright/test').Page, fullName: string, prefix: string) {
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

test.describe('Categories', () => {
  test('the system categories seeded in the baseline migration are visible', async ({ page }) => {
    await registerAndLand(page, 'Priya Categories', 'priya.categories')

    await page.getByTestId('nav-categories').click()
    await expect(page).toHaveURL(/\/categories$/)

    const systemGrid = page.getByTestId('system-categories-grid')
    await expect(systemGrid).toContainText('Food')
    await expect(systemGrid).toContainText('Salary')
  })

  test('a user can add a custom category and see it listed under Your categories', async ({ page }) => {
    await registerAndLand(page, 'Priya Adds', 'priya.add')
    await page.getByTestId('nav-categories').click()

    await page.getByTestId('add-category-button').click()
    await page.getByTestId('category-name-input').fill('Pet Care')
    await page.getByTestId('category-type-toggle').getByText('Income').click()
    await page.getByTestId('icon-option-heart').click()
    await page.getByTestId('category-save-button').click()

    await expect(page.getByTestId('category-editor-dialog')).toBeHidden()
    const customGrid = page.getByTestId('custom-categories-grid')
    await expect(customGrid).toContainText('Pet Care')
    await expect(customGrid).toContainText('Income')
  })

  test('a user can edit and then delete their own custom category', async ({ page }) => {
    await registerAndLand(page, 'Priya Edits', 'priya.edit')
    await page.getByTestId('nav-categories').click()

    await page.getByTestId('add-category-button').click()
    await page.getByTestId('category-name-input').fill('Book Club')
    await page.getByTestId('category-save-button').click()
    await expect(page.getByTestId('category-editor-dialog')).toBeHidden()

    await page.locator('button[data-testid^="category-edit-button-"]').first().click()
    await page.getByTestId('category-name-input').fill('Book Club Renamed')
    await page.getByTestId('category-save-button').click()
    await expect(page.getByTestId('category-editor-dialog')).toBeHidden()
    await expect(page.getByTestId('custom-categories-grid')).toContainText('Book Club Renamed')

    await page.locator('button[data-testid^="category-delete-button-"]').first().click()
    await expect(page.getByTestId('custom-categories-empty-state')).toBeVisible()
  })

  test('creating a category with a name and type that already exists shows an inline error', async ({ page }) => {
    await registerAndLand(page, 'Priya Duplicates', 'priya.duplicate')
    await page.getByTestId('nav-categories').click()

    await page.getByTestId('add-category-button').click()
    await page.getByTestId('category-name-input').fill('Groceries')
    await page.getByTestId('category-save-button').click()
    await expect(page.getByTestId('category-editor-dialog')).toBeHidden()

    await page.getByTestId('add-category-button').click()
    await page.getByTestId('category-name-input').fill('Groceries')
    await page.getByTestId('category-save-button').click()

    await expect(page.getByTestId('category-save-error-banner')).toContainText('already have a category')
  })
})
