<script setup lang="ts">
import { useAuthStore } from '../stores/auth'
import AppLayout from '../layouts/AppLayout.vue'

const authStore = useAuthStore()
</script>

<template>
  <AppLayout>
    <div class="flex flex-col items-center justify-center gap-2 py-24 bg-white border border-surface-200 rounded-lg">
      <h1 data-testid="dashboard-welcome" class="text-xl font-bold text-surface-900">
        Welcome back, {{ authStore.user?.fullName }}
      </h1>
      <p class="text-sm text-surface-500">
        Transactions, budgets, and reports land here in a future module.
      </p>
    </div>
  </AppLayout>
</template>
