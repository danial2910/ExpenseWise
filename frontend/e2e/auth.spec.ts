import { test, expect, request } from '@playwright/test'
import { promoteToAdmin } from './db'

// Trailing slash matters: Playwright resolves relative request paths via
// the WHATWG URL algorithm, which treats a leading "/" as root-relative
// and would silently drop "/api/v1" from the base otherwise.
const API_BASE_URL = 'http://localhost:8080/api/v1/'

function uniqueEmail(prefix: string): string {
  return `${prefix}+${Date.now()}-${Math.floor(Math.random() * 10000)}@example.com`
}

test.describe('Authentication', () => {
  test('register logs the user straight in and lands on the dashboard', async ({ page }) => {
    const email = uniqueEmail('sarah.register')

    await page.goto('/register')
    await page.getByTestId('register-fullname-input').fill('Sarah Lim')
    await page.getByTestId('register-email-input').fill(email)
    await page.locator('[data-testid=register-password-input] input').fill('Passw0rd1')
    await page.locator('[data-testid=register-confirm-password-input] input').fill('Passw0rd1')
    await page.getByTestId('register-submit-button').click()

    await expect(page).toHaveURL(/\/dashboard$/)
    await expect(page.getByTestId('dashboard-welcome')).toContainText('Sarah Lim')
  })

  test('registering with an already-used email shows an inline error', async ({ page }) => {
    const email = uniqueEmail('sarah.duplicate')

    await page.goto('/register')
    await page.getByTestId('register-fullname-input').fill('Sarah Lim')
    await page.getByTestId('register-email-input').fill(email)
    await page.locator('[data-testid=register-password-input] input').fill('Passw0rd1')
    await page.locator('[data-testid=register-confirm-password-input] input').fill('Passw0rd1')
    await page.getByTestId('register-submit-button').click()
    await expect(page).toHaveURL(/\/dashboard$/)
    await page.getByTestId('logout-button').click()
    await expect(page).toHaveURL(/\/login$/)

    await page.goto('/register')
    await page.getByTestId('register-fullname-input').fill('Sarah Lim')
    await page.getByTestId('register-email-input').fill(email)
    await page.locator('[data-testid=register-password-input] input').fill('Passw0rd1')
    await page.locator('[data-testid=register-confirm-password-input] input').fill('Passw0rd1')
    await page.getByTestId('register-submit-button').click()

    await expect(page.getByTestId('register-email-error')).toContainText('already exists')
  })

  test('login with the wrong password shows an error and does not sign in', async ({ page }) => {
    const email = uniqueEmail('sarah.login')

    await page.goto('/register')
    await page.getByTestId('register-fullname-input').fill('Sarah Lim')
    await page.getByTestId('register-email-input').fill(email)
    await page.locator('[data-testid=register-password-input] input').fill('Passw0rd1')
    await page.locator('[data-testid=register-confirm-password-input] input').fill('Passw0rd1')
    await page.getByTestId('register-submit-button').click()
    await expect(page).toHaveURL(/\/dashboard$/)
    await page.getByTestId('logout-button').click()
    await expect(page).toHaveURL(/\/login$/)

    await page.getByTestId('login-email-input').fill(email)
    await page.locator('[data-testid=login-password-input] input').fill('WrongPassword1')
    await page.getByTestId('login-submit-button').click()

    await expect(page.getByTestId('login-error-banner')).toContainText('Incorrect email or password')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('logout returns to login and blocks the dashboard', async ({ page }) => {
    const email = uniqueEmail('sarah.logout')

    await page.goto('/register')
    await page.getByTestId('register-fullname-input').fill('Sarah Lim')
    await page.getByTestId('register-email-input').fill(email)
    await page.locator('[data-testid=register-password-input] input').fill('Passw0rd1')
    await page.locator('[data-testid=register-confirm-password-input] input').fill('Passw0rd1')
    await page.getByTestId('register-submit-button').click()
    await expect(page).toHaveURL(/\/dashboard$/)

    await page.getByTestId('logout-button').click()
    await expect(page).toHaveURL(/\/login$/)

    await page.goto('/dashboard')
    await expect(page).toHaveURL(/\/login/)
  })

  test('forgot password shows the check-your-email screen regardless of whether the email is known', async ({ page }) => {
    await page.goto('/forgot-password')
    await page.getByTestId('forgot-email-input').fill('nobody-' + Date.now() + '@example.com')
    await page.getByTestId('forgot-submit-button').click()

    await expect(page).toHaveURL(/\/check-email/);
    await expect(page.getByTestId('check-email-message')).toContainText('We sent a password reset link')
  })

  test('a disabled user is blocked immediately, even mid-session', async ({ page }) => {
    const userEmail = uniqueEmail('sarah.disabled')
    const adminEmail = uniqueEmail('admin.disabler')
    const password = 'Passw0rd1'

    // The user being disabled, in its own browser session.
    await page.goto('/register')
    await page.getByTestId('register-fullname-input').fill('Sarah Lim')
    await page.getByTestId('register-email-input').fill(userEmail)
    await page.locator('[data-testid=register-password-input] input').fill(password)
    await page.locator('[data-testid=register-confirm-password-input] input').fill(password)
    await page.getByTestId('register-submit-button').click()
    await expect(page).toHaveURL(/\/dashboard$/)

    // An admin, promoted directly in the DB since there's no admin UI in this phase.
    const apiContext = await request.newContext({ baseURL: API_BASE_URL })
    const registerAdminResponse = await apiContext.post('auth/register', {
      data: { fullName: 'Admin Disabler', email: adminEmail, password },
    })
    expect(registerAdminResponse.ok()).toBeTruthy()
    await promoteToAdmin(adminEmail)

    const adminLoginResponse = await apiContext.post('auth/login', { data: { email: adminEmail, password } })
    expect(adminLoginResponse.ok()).toBeTruthy()
    const { accessToken: adminToken } = await adminLoginResponse.json()

    const usersPage = await apiContext.get('admin/users?search=' + encodeURIComponent(userEmail), {
      headers: { Authorization: `Bearer ${adminToken}` },
    })
    expect(usersPage.ok()).toBeTruthy()
    const { content } = await usersPage.json()
    const targetUserId = content[0].id

    const disableResponse = await apiContext.patch(`admin/users/${targetUserId}/status`, {
      data: { active: false },
      headers: { Authorization: `Bearer ${adminToken}` },
    })
    expect(disableResponse.ok()).toBeTruthy()

    // The disabled user's still-open session must be rejected on its very next request.
    await page.goto('/dashboard')
    await expect(page).toHaveURL(/\/login/)
  })

  test('a registered user can log back in and reach the dashboard', async ({ page }) => {
  const email = uniqueEmail('sarah.returning')
  const password = 'Passw0rd1'

  // Arrange: create the account, then log out so we start from a clean /login.
  await page.goto('/register')
  await page.getByTestId('register-fullname-input').fill('Sarah Lim')
  await page.getByTestId('register-email-input').fill(email)
  await page.locator('[data-testid=register-password-input] input').fill(password)
  await page.locator('[data-testid=register-confirm-password-input] input').fill(password)
  await page.getByTestId('register-submit-button').click()
  await expect(page).toHaveURL(/\/dashboard$/)
  await page.getByTestId('logout-button').click()
  await expect(page).toHaveURL(/\/login$/)

  // Act: log in with the SAME credentials.
  await page.getByTestId('login-email-input').fill(email)
  await page.locator('[data-testid=login-password-input] input').fill(password)
  await page.getByTestId('login-submit-button').click()

  // Assert: we land on the dashboard and it greets us by name.
  await expect(page).toHaveURL(/\/dashboard$/)
  await expect(page.getByTestId('dashboard-welcome')).toContainText('Sarah Lim')
  })
})
