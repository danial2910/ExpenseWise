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

test.describe('Responsive shell', () => {
  test('the mobile chrome (bottom nav + FAB) shows and the sidebar hides below md', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await registerAndLand(page, 'Mobile Shell', 'responsive.mobile')

    await expect(page.getByTestId('bottom-nav')).toBeVisible()
    await expect(page.getByTestId('fab-button')).toBeVisible()
    await expect(page.getByTestId('nav-dashboard')).toBeHidden()

    // USER's 9 nav items overflow the 4 direct tabs into a "More" sheet.
    await page.getByTestId('bottom-nav-more').click()
    await expect(page.getByTestId('more-sheet-link-profile')).toBeVisible()
  })

  test('the desktop sidebar shows and the mobile chrome hides at desktop width', async ({ page }) => {
    await registerAndLand(page, 'Desktop Shell', 'responsive.desktop')

    await expect(page.getByTestId('nav-dashboard')).toBeVisible()
    await expect(page.getByTestId('bottom-nav')).toBeHidden()
  })

  test("an admin's 4 nav items fit the bottom tab bar directly, with no More tab", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    const email = await registerAndLand(page, 'Mobile Admin', 'responsive.admin')
    await promoteToAdmin(email)
    await page.reload()
    await expect(page).toHaveURL(/\/admin\/dashboard$/)

    await expect(page.getByTestId('bottom-nav')).toBeVisible()
    await expect(page.getByTestId('bottom-nav-admin-dashboard')).toBeVisible()
    await expect(page.getByTestId('bottom-nav-more')).toBeHidden()
  })
})
