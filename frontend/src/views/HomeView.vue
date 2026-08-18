<script setup lang="ts">
import { ref, onMounted } from 'vue'
import http from '../api/http'

type Status = 'loading' | 'up' | 'down'

const status = ref<Status>('loading')
const detail = ref<string>('')

const badgeClasses: Record<Status, string> = {
  up: 'bg-success-bg text-success',
  down: 'bg-danger-bg text-danger',
  loading: 'bg-surface-100 text-surface-500',
}

async function checkHealth() {
  status.value = 'loading'
  detail.value = ''
  try {
    const { data } = await http.get('/health')
    status.value = data.status === 'UP' ? 'up' : 'down'
    detail.value = JSON.stringify(data)
  } catch {
    status.value = 'down'
    detail.value = 'Request failed'
  }
}

onMounted(checkHealth)
</script>

<template>
  <div class="min-h-screen bg-surface-100 p-8">
    <h1 class="font-display text-2xl font-semibold tracking-tight text-surface-900 mb-4">ExpenseWise — API Health</h1>
    <div data-testid="health-status-badge" :class="badgeClasses[status]" class="inline-block px-3 py-1 rounded-lg text-sm font-semibold">
      {{ status.toUpperCase() }}
    </div>
    <p class="mt-2 text-sm text-surface-500">{{ detail }}</p>
    <button
      data-testid="health-retry-button"
      class="mt-4 px-4 py-2 bg-primary-500 text-surface-100 rounded-lg text-sm font-semibold active:scale-[0.98] transition-transform duration-fast ease-out-expo"
      @click="checkHealth"
    >
      Retry
    </button>
  </div>
</template>
