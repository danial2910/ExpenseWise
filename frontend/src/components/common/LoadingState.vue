<script setup lang="ts">
withDefaults(defineProps<{ label?: string; testid?: string }>(), {
  label: 'Loading…',
  testid: undefined,
})
</script>

<template>
  <div
    :data-testid="testid ?? 'loading-state'"
    class="flex flex-col items-center justify-center gap-3 py-16 px-6"
    role="status"
  >
    <i class="pi pi-spin pi-spinner text-2xl text-primary-400" />
    <p class="text-sm text-surface-500">{{ label }}</p>
  </div>
</template>
