<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import AuthLayout from '../../layouts/AuthLayout.vue'
import http from '../../api/http'

const route = useRoute()
const email = typeof route.query.email === 'string' ? route.query.email : ''

const resending = ref(false)
const resent = ref(false)

async function onResend() {
  if (!email) return
  resending.value = true
  try {
    // Same fire-and-forget endpoint as the original request — the backend
    // never confirms whether the address exists, so there's nothing to
    // branch on here either.
    await http.post('/auth/forgot-password', { email })
  } finally {
    resending.value = false
    resent.value = true
  }
}
</script>

<template>
  <AuthLayout>
    <div class="flex flex-col items-center gap-3 text-center">
      <div class="w-14 h-14 rounded-full flex items-center justify-center bg-gradient-to-br from-aurora-emerald via-aurora-teal to-aurora-blue shadow-glow-accent">
        <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="var(--color-surface-100)" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <rect x="2" y="4" width="20" height="16" rx="2" />
          <path d="M2 6l10 7 10-7" />
        </svg>
      </div>
      <h1 class="font-display text-xl font-semibold tracking-tight text-surface-900 mt-1">Check your email</h1>
      <p data-testid="check-email-message" class="text-sm text-surface-500 max-w-xs">
        We sent a password reset link to <span class="text-surface-700 font-medium">{{ email }}</span>. The link expires shortly, so use it soon.
      </p>

      <Transition name="field-in">
        <p v-if="resent" data-testid="check-email-resent-confirmation" class="text-xs text-success flex items-center gap-1.5 mt-1">
          <i class="pi pi-check-circle" />
          Email resent
        </p>
        <button
          v-else
          data-testid="check-email-resend-button"
          type="button"
          class="text-xs font-semibold text-surface-500 hover:text-surface-800 mt-1 transition-colors duration-fast ease-out-expo disabled:opacity-50"
          :disabled="resending"
          @click="onResend"
        >
          {{ resending ? 'Resending…' : "Didn't get it? Resend email" }}
        </button>
      </Transition>

      <router-link data-testid="check-email-login-link" to="/login" class="text-sm text-primary-300 hover:text-primary-200 font-semibold mt-3 transition-colors duration-fast ease-out-expo">
        Back to login
      </router-link>
    </div>
  </AuthLayout>
</template>
