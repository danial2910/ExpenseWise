<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import AuthLayout from '../../layouts/AuthLayout.vue'
import http from '../../api/http'

const router = useRouter()
const email = ref('')
const submitting = ref(false)

async function onSubmit() {
  submitting.value = true
  try {
    // The backend always returns the same response whether or not the
    // email exists, so there's nothing to branch on here — just proceed.
    await http.post('/auth/forgot-password', { email: email.value })
  } finally {
    submitting.value = false
    router.push({ name: 'check-email', query: { email: email.value } })
  }
}
</script>

<template>
  <AuthLayout title="Forgot password?" subtitle="Enter your email and we'll send you a reset link">
    <form class="flex flex-col gap-4" @submit.prevent="onSubmit">
      <div>
        <label for="forgot-email" class="block text-xs font-semibold text-surface-600 mb-1.5">Email</label>
        <IconField class="w-full">
          <InputIcon class="pi pi-envelope" />
          <InputText id="forgot-email" v-model="email" type="email" data-testid="forgot-email-input" class="w-full" required />
        </IconField>
      </div>

      <Button
        type="submit"
        label="Send reset link"
        data-testid="forgot-submit-button"
        class="w-full active:scale-[0.98] transition-transform duration-fast ease-out-expo"
        :loading="submitting"
      />

      <p class="text-sm text-surface-500 text-center">
        Remembered your password?
        <router-link data-testid="forgot-login-link" to="/login" class="text-primary-300 hover:text-primary-200 font-semibold transition-colors duration-fast ease-out-expo">
          Sign in
        </router-link>
      </p>
    </form>
  </AuthLayout>
</template>
