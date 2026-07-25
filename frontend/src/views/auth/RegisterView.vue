<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import AuthLayout from '../../layouts/AuthLayout.vue'
import { useAuthStore } from '../../stores/auth'
import type { ApiErrorResponse } from '../../types/auth'
import { isAxiosError } from 'axios'

const router = useRouter()
const authStore = useAuthStore()

const fullName = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const submitting = ref(false)

const emailError = ref('')
const passwordError = ref('')
const confirmError = ref('')

function resetErrors() {
  emailError.value = ''
  passwordError.value = ''
  confirmError.value = ''
}

async function onSubmit() {
  resetErrors()

  if (password.value !== confirmPassword.value) {
    confirmError.value = 'Passwords do not match.'
    return
  }

  submitting.value = true
  try {
    await authStore.register(fullName.value, email.value, password.value)
    router.push('/dashboard')
  } catch (error) {
    if (isAxiosError<ApiErrorResponse>(error) && error.response?.data.fieldErrors) {
      const fieldErrors = error.response.data.fieldErrors
      emailError.value = fieldErrors.email ?? ''
      passwordError.value = fieldErrors.password ?? ''
    } else {
      emailError.value = 'Something went wrong. Please try again.'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout title="Create your account" subtitle="Start tracking your finances">
    <form class="flex flex-col gap-4" @submit.prevent="onSubmit">
      <div>
        <label for="register-name" class="block text-xs font-semibold text-surface-600 mb-1.5">Full name</label>
        <InputText id="register-name" v-model="fullName" data-testid="register-fullname-input" class="w-full" required />
      </div>

      <div>
        <label for="register-email" class="block text-xs font-semibold text-surface-600 mb-1.5">Email</label>
        <InputText
          id="register-email"
          v-model="email"
          type="email"
          data-testid="register-email-input"
          class="w-full"
          :invalid="!!emailError"
          required
        />
        <p v-if="emailError" data-testid="register-email-error" class="text-xs text-red-600 mt-1.5">
          {{ emailError }}
        </p>
      </div>

      <div>
        <label for="register-password" class="block text-xs font-semibold text-surface-600 mb-1.5">Password</label>
        <Password
          id="register-password"
          v-model="password"
          data-testid="register-password-input"
          input-class="w-full"
          class="w-full"
          :feedback="false"
          toggle-mask
          :invalid="!!passwordError"
          required
        />
        <p v-if="passwordError" data-testid="register-password-error" class="text-xs text-red-600 mt-1.5">
          {{ passwordError }}
        </p>
      </div>

      <div>
        <label for="register-confirm" class="block text-xs font-semibold text-surface-600 mb-1.5">Confirm password</label>
        <Password
          id="register-confirm"
          v-model="confirmPassword"
          data-testid="register-confirm-password-input"
          input-class="w-full"
          class="w-full"
          :feedback="false"
          toggle-mask
          :invalid="!!confirmError"
          required
        />
        <p v-if="confirmError" data-testid="register-confirm-error" class="text-xs text-red-600 mt-1.5">
          {{ confirmError }}
        </p>
      </div>

      <Button
        type="submit"
        label="Create account"
        data-testid="register-submit-button"
        class="w-full"
        :loading="submitting"
      />

      <p class="text-sm text-surface-500 text-center">
        Already have an account?
        <router-link data-testid="register-login-link" to="/login" class="text-primary-600 font-semibold">
          Sign in
        </router-link>
      </p>
    </form>
  </AuthLayout>
</template>
