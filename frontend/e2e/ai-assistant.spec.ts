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

async function goToAiAssistant(page: Page) {
  await page.getByTestId('nav-ai-assistant').click()
  await expect(page).toHaveURL(/\/ai-assistant$/)
}

// The backend calls Groq synchronously per exchange (non-streaming), and
// the UI itself warns this can take up to 15 seconds — matches the
// existing test's timeout.
const AI_REPLY_TIMEOUT = 30000

// Scoped to actual chat bubbles only. `[data-testid^="ai-message-"]` alone
// also matches `ai-message-input` (the text box), and page-wide getByText
// also matches the history rail (its title is the verbatim first message),
// so both need excluding explicitly.
function messageBubbles(page: Page) {
  return page.locator('[data-testid^="ai-message-"]:not([data-testid="ai-message-input"])')
}

async function sendAndWaitForReply(page: Page, text: string) {
  await page.getByTestId('ai-message-input').fill(text)
  await page.getByTestId('ai-send-button').click()
  await expect(messageBubbles(page).filter({ hasText: text }).first()).toBeVisible()
  await expect(page.getByTestId('ai-loading-indicator')).toBeHidden({ timeout: AI_REPLY_TIMEOUT })
  await expect(page.getByTestId('ai-error-banner')).toBeHidden()
}

test.describe('AI Assistant', () => {
  test('a user can open the AI Assistant, send a message, and see an assistant reply render', async ({ page }) => {
    // Do NOT assert on the reply's content (CLAUDE.md: AI responses are
    // non-deterministic) — only that a reply renders in the thread.
    await registerAndLand(page, 'Priya Assistant', 'priya.assistant')
    await goToAiAssistant(page)

    await expect(page.getByTestId('ai-empty-state')).toBeVisible()
    await expect(page.getByTestId('ai-disclaimer')).toBeVisible()

    await page.getByTestId('ai-message-input').fill('How much did I spend this month?')
    await page.getByTestId('ai-send-button').click()

    // The user's own message renders immediately (optimistic).
    await expect(page.getByText('How much did I spend this month?')).toBeVisible()

    await expect(page.getByTestId('ai-loading-indicator')).toBeHidden({ timeout: AI_REPLY_TIMEOUT })
    const assistantMessages = page.locator('[data-testid^="ai-message-"]').filter({ hasNot: page.getByText('How much did I spend this month?') })
    await expect(assistantMessages.first()).toBeVisible()
    await expect(page.getByTestId('ai-error-banner')).toBeHidden()

    // The conversation now appears in the history rail with a derived title.
    await expect(page.locator('[data-testid^="ai-conversation-item-"]').first()).toBeVisible()
  })

  test('the send button stays disabled for empty or whitespace-only input', async ({ page }) => {
    await registerAndLand(page, 'Priya Disabled', 'priya.disabled')
    await goToAiAssistant(page)

    await expect(page.getByTestId('ai-send-button')).toBeDisabled()

    await page.getByTestId('ai-message-input').fill('   ')
    await expect(page.getByTestId('ai-send-button')).toBeDisabled()

    await page.getByTestId('ai-message-input').fill('Real question')
    await expect(page.getByTestId('ai-send-button')).toBeEnabled()
  })

  test('clicking a suggested prompt sends it as the first message', async ({ page }) => {
    await registerAndLand(page, 'Priya Suggested', 'priya.suggested')
    await goToAiAssistant(page)

    const prompt = page.getByTestId('ai-suggested-prompt-0')
    const promptText = await prompt.textContent()
    await prompt.click()

    await expect(page.getByText(promptText!.trim(), { exact: true })).toBeVisible()
    await expect(page.getByTestId('ai-loading-indicator')).toBeHidden({ timeout: AI_REPLY_TIMEOUT })
    await expect(page.getByTestId('ai-error-banner')).toBeHidden()
  })

  test('a user can send a second message in the same conversation', async ({ page }) => {
    await registerAndLand(page, 'Priya Followup', 'priya.followup')
    await goToAiAssistant(page)

    await sendAndWaitForReply(page, 'What is my balance?')
    await sendAndWaitForReply(page, 'And what about last month?')

    // 2 user + 2 assistant messages, still one conversation in the rail.
    await expect(messageBubbles(page)).toHaveCount(4)
    await expect(page.locator('[data-testid^="ai-conversation-item-"]')).toHaveCount(1)
  })

  test('starting a new chat resets the view but keeps the previous conversation in history', async ({ page }) => {
    await registerAndLand(page, 'Priya Newchat', 'priya.newchat')
    await goToAiAssistant(page)

    await sendAndWaitForReply(page, 'How much did I spend on dining?')
    await expect(page.locator('[data-testid^="ai-conversation-item-"]')).toHaveCount(1)

    await page.getByTestId('ai-new-chat-button').click()

    await expect(page.getByTestId('ai-empty-state')).toBeVisible()
    await expect(page.locator('[data-testid^="ai-conversation-item-"]')).toHaveCount(1)
  })

  test('a user can switch between two conversations and see the correct messages for each', async ({ page }) => {
    await registerAndLand(page, 'Priya Switch', 'priya.switch')
    await goToAiAssistant(page)

    const firstMessage = 'What is my current balance'
    const secondMessage = 'List my top spending categories'

    await sendAndWaitForReply(page, firstMessage)
    await page.getByTestId('ai-new-chat-button').click()
    await sendAndWaitForReply(page, secondMessage)

    // The second (newest) conversation is active and showing its own message.
    // (The first message's title is still visible in the history rail —
    // only the chat pane itself should not show it.)
    await expect(messageBubbles(page).filter({ hasText: firstMessage })).toHaveCount(0)
    await expect(messageBubbles(page).filter({ hasText: secondMessage })).toBeVisible()

    // Switch to the first conversation via the history rail (its title is
    // the verbatim first message, since it's under the 60-char limit).
    await page.getByText(firstMessage, { exact: true }).first().click()
    await expect(page.getByTestId('ai-chat-loading')).toBeHidden()
    await expect(messageBubbles(page).filter({ hasText: firstMessage })).toBeVisible()
    await expect(messageBubbles(page).filter({ hasText: secondMessage })).toHaveCount(0)
  })

  test('deleting the active conversation removes it and resets to the empty state', async ({ page }) => {
    await registerAndLand(page, 'Priya Delete', 'priya.delete')
    await goToAiAssistant(page)

    await sendAndWaitForReply(page, 'Am I on track with my budgets?')
    await expect(page.locator('[data-testid^="ai-conversation-item-"]')).toHaveCount(1)

    await page.locator('button[data-testid^="ai-delete-conversation-"]').first().click()

    await expect(page.getByTestId('ai-conversations-empty')).toBeVisible()
    await expect(page.getByTestId('ai-empty-state')).toBeVisible()
  })

  test('a message over the 2000-character limit is rejected with the error banner and preserves the input', async ({ page }) => {
    await registerAndLand(page, 'Priya Toolong', 'priya.toolong')
    await goToAiAssistant(page)

    const overLong = 'a'.repeat(2001)
    await page.getByTestId('ai-message-input').fill(overLong)
    await page.getByTestId('ai-send-button').click()

    // Validation fails server-side before any Groq call is made, so this
    // resolves fast — no need for the long AI-reply timeout.
    await expect(page.getByTestId('ai-error-banner')).toBeVisible()
    await expect(page.getByTestId('ai-empty-state')).toBeVisible()
    await expect(page.getByTestId('ai-message-input')).toHaveValue(overLong)
  })

  test('the spending analysis panel shows the empty state for a brand-new user with no data', async ({ page }) => {
    await registerAndLand(page, 'Priya Insights', 'priya.insights')
    await goToAiAssistant(page)

    await expect(page.getByTestId('ai-insights-empty')).toBeVisible()
  })
})
