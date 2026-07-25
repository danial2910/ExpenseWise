<script setup lang="ts">
defineProps<{ message: string; testid?: string }>()
</script>

<template>
  <div
    :data-testid="testid ?? 'form-error-banner'"
    class="bg-red-50 border border-red-300 text-red-600 text-sm rounded-lg px-3 py-2.5 mb-4"
  >
    {{ message }}
  </div>
</template>
