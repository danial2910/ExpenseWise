<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import AuthLayout from '../../layouts/AuthLayout.vue'
import FormError from '../../components/common/FormError.vue'
import { useAuthStore } from '../../stores/auth'
import { useAppStore } from '../../stores/app'
import type { ApiErrorResponse } from '../../types/auth'
import { isAxiosError } from 'axios'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const appStore = useAppStore()

const email = ref('')
const password = ref('')
const errorMessage = ref('')
const submitting = ref(false)

// Set by ResetPasswordView right before it redirects here on a successful
// password change — a one-shot flash message, not a URL query param, so it
// can't ever be reflected in the URL (login-related routes are asserted on
// with plain `/\/login$/` regexes in e2e/auth.spec.ts).
const flashMessage = appStore.consumeFlashMessage()

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
    <Transition name="field-in">
      <div
        v-if="flashMessage"
        data-testid="login-flash-banner"
        class="flex items-center gap-2.5 bg-success-bg border border-success/30 text-success text-sm rounded-lg px-3 py-2.5 mb-4"
      >
        <i class="pi pi-check-circle text-[13px]" />
        <span>{{ flashMessage }}</span>
      </div>
    </Transition>

    <Transition name="field-in">
      <FormError v-if="errorMessage" :message="errorMessage" testid="login-error-banner" />
    </Transition>

    <form class="flex flex-col gap-4" @submit.prevent="onSubmit">
      <div>
        <label for="login-email" class="block text-xs font-semibold text-surface-600 mb-1.5">Email</label>
        <IconField class="w-full">
          <InputIcon class="pi pi-envelope" />
          <InputText
            id="login-email"
            v-model="email"
            type="email"
            data-testid="login-email-input"
            class="w-full"
            :invalid="!!errorMessage"
            required
          />
        </IconField>
      </div>

      <div>
        <div class="flex items-center justify-between mb-1.5">
          <label for="login-password" class="block text-xs font-semibold text-surface-600">Password</label>
          <router-link data-testid="login-forgot-password-link" to="/forgot-password" class="text-xs font-semibold text-primary-300 hover:text-primary-200 transition-colors duration-fast ease-out-expo">
            Forgot password?
          </router-link>
        </div>
        <IconField class="w-full">
          <InputIcon class="pi pi-lock" />
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
        </IconField>
      </div>

      <Button
        type="submit"
        label="Sign in"
        data-testid="login-submit-button"
        class="w-full active:scale-[0.98] transition-transform duration-fast ease-out-expo"
        :loading="submitting"
      />

      <p class="text-sm text-surface-500 text-center">
        Don't have an account?
        <router-link data-testid="login-register-link" to="/register" class="text-primary-300 hover:text-primary-200 font-semibold transition-colors duration-fast ease-out-expo">
          Sign up
        </router-link>
      </p>
    </form>
  </AuthLayout>
</template>
