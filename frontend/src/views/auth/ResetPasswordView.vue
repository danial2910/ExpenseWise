<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Password from 'primevue/password'
import Button from 'primevue/button'
import AuthLayout from '../../layouts/AuthLayout.vue'
import FormError from '../../components/common/FormError.vue'
import PasswordStrengthMeter from '../../components/common/PasswordStrengthMeter.vue'
import http from '../../api/http'
import type { ApiErrorResponse } from '../../types/auth'
import { isAxiosError } from 'axios'

const route = useRoute()
const router = useRouter()

const token = typeof route.query.token === 'string' ? route.query.token : ''
const newPassword = ref('')
const confirmPassword = ref('')
const errorMessage = ref('')
const submitting = ref(false)

async function onSubmit() {
  errorMessage.value = ''

  if (newPassword.value !== confirmPassword.value) {
    errorMessage.value = 'Passwords do not match.'
    return
  }

  submitting.value = true
  try {
    await http.post('/auth/reset-password', { token, newPassword: newPassword.value })
    router.push('/login')
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
</script>

<template>
  <AuthLayout title="Set a new password" subtitle="Choose a strong password for your account">
    <FormError v-if="errorMessage" :message="errorMessage" testid="reset-error-banner" />

    <form class="flex flex-col gap-4" @submit.prevent="onSubmit">
      <div>
        <label for="reset-password" class="block text-xs font-semibold text-surface-600 mb-1.5">New password</label>
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
        <PasswordStrengthMeter :password="newPassword" />
      </div>

      <div>
        <label for="reset-confirm" class="block text-xs font-semibold text-surface-600 mb-1.5">Confirm new password</label>
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
      </div>

      <Button
        type="submit"
        label="Reset password"
        data-testid="reset-submit-button"
        class="w-full"
        :loading="submitting"
      />
    </form>
  </AuthLayout>
</template>
