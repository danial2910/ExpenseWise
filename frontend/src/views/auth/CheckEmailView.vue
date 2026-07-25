<script setup lang="ts">
import { useRoute } from 'vue-router'
import AuthLayout from '../../layouts/AuthLayout.vue'

const route = useRoute()
const email = typeof route.query.email === 'string' ? route.query.email : ''
</script>

<template>
  <AuthLayout>
    <div class="flex flex-col items-center gap-3 text-center">
      <div class="w-12 h-12 rounded-lg bg-surface-100 flex items-center justify-center">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#4F46E5" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
          <rect x="2" y="4" width="20" height="16" rx="2" />
          <path d="M2 6l10 7 10-7" />
        </svg>
      </div>
      <h1 class="text-lg font-bold text-surface-900">Check your email</h1>
      <p data-testid="check-email-message" class="text-sm text-surface-500">
        We sent a password reset link to {{ email }}
      </p>
      <router-link data-testid="check-email-login-link" to="/login" class="text-sm text-primary-600 font-semibold mt-2">
        Back to login
      </router-link>
    </div>
  </AuthLayout>
</template>
