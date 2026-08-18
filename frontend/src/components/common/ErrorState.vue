<script setup lang="ts">
withDefaults(
  defineProps<{ title?: string; description?: string; testid?: string; retryTestid?: string }>(),
  { title: 'Something went wrong', description: 'Please try again.', testid: undefined, retryTestid: 'error-state-retry' },
)
const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <div
    :data-testid="testid ?? 'error-state'"
    class="flex flex-col items-center justify-center text-center gap-3 py-16 px-6"
  >
    <div class="w-12 h-12 rounded-full bg-danger-bg border border-danger/30 flex items-center justify-center">
      <i class="pi pi-exclamation-triangle text-xl text-danger" />
    </div>
    <div class="flex flex-col gap-1">
      <p class="font-display text-base font-semibold text-surface-900">{{ title }}</p>
      <p class="text-sm text-surface-500 max-w-sm">{{ description }}</p>
    </div>
    <button
      :data-testid="retryTestid"
      type="button"
      class="mt-1 text-sm font-medium text-primary-300 hover:text-primary-200 transition-colors duration-fast ease-out-expo"
      @click="emit('retry')"
    >
      Try again
    </button>
  </div>
</template>
