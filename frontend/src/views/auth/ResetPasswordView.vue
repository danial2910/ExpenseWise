<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import Password from 'primevue/password'
import Button from 'primevue/button'
import AuthLayout from '../../layouts/AuthLayout.vue'
import FormError from '../../components/common/FormError.vue'
import PasswordStrengthMeter from '../../components/common/PasswordStrengthMeter.vue'
import { useAppStore } from '../../stores/app'
import http from '../../api/http'
import type { ApiErrorResponse } from '../../types/auth'
import { isAxiosError } from 'axios'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const token = typeof route.query.token === 'string' ? route.query.token : ''
const newPassword = ref('')
const confirmPassword = ref('')
const errorMessage = ref('')
const submitting = ref(false)
const success = ref(false)

async function onSubmit() {
  errorMessage.value = ''

  if (newPassword.value !== confirmPassword.value) {
    errorMessage.value = 'Passwords do not match.'
    return
  }

  submitting.value = true
  try {
    await http.post('/auth/reset-password', { token, newPassword: newPassword.value })
    success.value = true
  } catch (error) {
    if (isAxiosError<ApiErrorResponse>(error) && error.response?.data.error === 'INVALID_TOKEN') {
      errorMessage.value = 'This reset link is invalid or has expired. Please request a new one.'
    } else if (isAxiosError<ApiErrorResponse>(error) && error.response?.data.fieldErrors?.password) {
      errorMessage.value = error.response.data.fieldErrors.password
    } else {
      errorMessage.value = 'Something went wrong. Please try again.'
    }
  } finally {
    submitting.value = false
  }
}

function onContinueToLogin() {
  appStore.setFlashMessage('Password updated — sign in with your new password.')
  router.push('/login')
}
</script>

<template>
  <AuthLayout
    :title="success ? undefined : 'Set a new password'"
    :subtitle="success ? undefined : 'Choose a strong password for your account'"
  >
    <div v-if="success" data-testid="reset-success-panel" class="flex flex-col items-center gap-3 text-center">
      <div class="w-14 h-14 rounded-full flex items-center justify-center bg-gradient-to-br from-aurora-emerald via-aurora-teal to-aurora-blue shadow-glow-accent">
        <i class="pi pi-check text-2xl text-surface-100" />
      </div>
      <h1 class="font-display text-xl font-semibold tracking-tight text-surface-900 mt-1">Password updated</h1>
      <p class="text-sm text-surface-500 max-w-xs">
        Your password has been changed. Sign in with your new password to continue.
      </p>
      <Button
        data-testid="reset-success-login-link"
        label="Continue to sign in"
        class="w-full mt-2 active:scale-[0.98] transition-transform duration-fast ease-out-expo"
        @click="onContinueToLogin"
      />
    </div>

    <template v-else>
      <Transition name="field-in">
        <FormError v-if="errorMessage" :message="errorMessage" testid="reset-error-banner" />
      </Transition>

      <form class="flex flex-col gap-4" @submit.prevent="onSubmit">
        <div>
          <label for="reset-password" class="block text-xs font-semibold text-surface-600 mb-1.5">New password</label>
          <IconField class="w-full">
            <InputIcon class="pi pi-lock" />
            <Password
              id="reset-password"
              v-model="newPassword"
              data-testid="reset-password-input"
              input-class="w-full"
              class="w-full"
              :feedback="false"
              toggle-mask
              required
            />
          </IconField>
          <PasswordStrengthMeter v-if="newPassword" :password="newPassword" />
        </div>

        <div>
          <label for="reset-confirm" class="block text-xs font-semibold text-surface-600 mb-1.5">Confirm new password</label>
          <IconField class="w-full">
            <InputIcon class="pi pi-lock" />
            <Password
              id="reset-confirm"
              v-model="confirmPassword"
              data-testid="reset-confirm-password-input"
              input-class="w-full"
              class="w-full"
              :feedback="false"
              toggle-mask
              required
            />
          </IconField>
        </div>

        <Button
          type="submit"
          label="Reset password"
          data-testid="reset-submit-button"
          class="w-full active:scale-[0.98] transition-transform duration-fast ease-out-expo"
          :loading="submitting"
        />
      </form>
    </template>
  </AuthLayout>
</template>
