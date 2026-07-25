<script setup lang="ts">
import { ref, onMounted } from 'vue'
import http from '../api/http'

type Status = 'loading' | 'up' | 'down'

const status = ref<Status>('loading')
const detail = ref<string>('')

const badgeClasses: Record<Status, string> = {
  up: 'bg-green-100 text-green-800',
  down: 'bg-red-100 text-red-800',
  loading: 'bg-gray-100 text-gray-800',
}

async function checkHealth() {
  status.value = 'loading'
  detail.value = ''
  try {
    const { data } = await http.get('/health')
    status.value = data.status === 'UP' ? 'up' : 'down'
    detail.value = JSON.stringify(data)
  } catch (e) {
    status.value = 'down'
    detail.value = 'Request failed'
  }
}

onMounted(checkHealth)
</script>

<template>
  <div class="p-8">
    <h1 class="text-2xl font-bold mb-4">ExpenseWise — API Health</h1>
    <div data-testid="health-status-badge" :class="badgeClasses[status]" class="inline-block px-3 py-1 rounded">
      {{ status.toUpperCase() }}
    </div>
    <p class="mt-2 text-sm text-gray-600">{{ detail }}</p>
    <button
      data-testid="health-retry-button"
      class="mt-4 px-4 py-2 bg-blue-600 text-white rounded"
      @click="checkHealth"
    >
      Retry
    </button>
  </div>
</template>
