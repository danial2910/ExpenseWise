import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    appName: 'ExpenseWise',
    // A one-shot message for the next page to show, set right before a
    // router.push (e.g. "password updated" on ResetPasswordView → Login).
    // Not persisted — cleared by whoever reads it, in-memory only.
    flashMessage: null as string | null,
  }),
  actions: {
    setFlashMessage(message: string) {
      this.flashMessage = message
    },
    consumeFlashMessage(): string | null {
      const message = this.flashMessage
      this.flashMessage = null
      return message
    },
  },
})
