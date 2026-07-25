<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import AuthLayout from '../../layouts/AuthLayout.vue'
import FormError from '../../components/common/FormError.vue'
import { useAuthStore } from '../../stores/auth'
import type { ApiErrorResponse } from '../../types/auth'
import { isAxiosError } from 'axios'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')
const errorMessage = ref('')
const submitting = ref(false)

async function onSubmit() {
  errorMessage.value = ''
  submitting.value = true
  try {
    await authStore.login(email.value, password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.push(redirect)
  } catch (error) {
    if (isAxiosError<ApiErrorResponse>(error) && error.response?.status === 429) {
      errorMessage.value = 'Too many login attempts. Please try again later.'
    } else {
      errorMessage.value = 'Incorrect email or password.'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout title="Welcome back" subtitle="Sign in to your account">
    <FormError v-if="errorMessage" :message="errorMessage" testid="login-error-banner" />

    <form class="flex flex-col gap-4" @submit.prevent="onSubmit">
      <div>
        <label for="login-email" class="block text-xs font-semibold text-surface-600 mb-1.5">Email</label>
        <InputText
          id="login-email"
          v-model="email"
          type="email"
          data-testid="login-email-input"
          class="w-full"
          :invalid="!!errorMessage"
          required
        />
      </div>

      <div>
        <label for="login-password" class="block text-xs font-semibold text-surface-600 mb-1.5">Password</label>
        <Password
          id="login-password"
          v-model="password"
          data-testid="login-password-input"
          input-class="w-full"
          class="w-full"
          :feedback="false"
          toggle-mask
          :invalid="!!errorMessage"
          required
        />
      </div>

      <div class="text-right -mt-2">
        <router-link data-testid="login-forgot-password-link" to="/forgot-password" class="text-sm text-primary-600 font-semibold">
          Forgot password?
        </router-link>
      </div>

      <Button
        type="submit"
        label="Sign in"
        data-testid="login-submit-button"
        class="w-full"
        :loading="submitting"
      />

      <p class="text-sm text-surface-500 text-center">
        Don't have an account?
        <router-link data-testid="login-register-link" to="/register" class="text-primary-600 font-semibold">
          Sign up
        </router-link>
      </p>
    </form>
  </AuthLayout>
</template>
