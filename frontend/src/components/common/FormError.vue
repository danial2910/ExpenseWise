<script setup lang="ts">
defineProps<{ message: string; testid?: string }>()
</script>

<template>
  <div
    :data-testid="testid ?? 'form-error-banner'"
    class="flex items-start gap-2.5 bg-danger-bg border border-danger/30 text-danger text-sm rounded-lg px-3 py-2.5 mb-4"
  >
    <i class="pi pi-exclamation-circle mt-0.5 text-[13px]" />
    <span>{{ message }}</span>
  </div>
</template>
