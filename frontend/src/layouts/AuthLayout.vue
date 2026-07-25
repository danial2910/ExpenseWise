<script setup lang="ts">
withDefaults(defineProps<{ title?: string; subtitle?: string }>(), {
  title: undefined,
  subtitle: undefined,
})
</script>

<template>
  <div class="min-h-screen bg-surface-100 flex items-center justify-center p-6">
    <div class="w-full max-w-[420px] flex flex-col items-center gap-6">
      <div class="flex items-center gap-2">
        <div class="w-8 h-8 rounded-lg bg-primary-600 flex items-center justify-center">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round">
            <path d="M4 17V9a2 2 0 0 1 2-2h9l5 5v5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2Z" />
            <path d="M15 7v4h4" />
          </svg>
        </div>
        <span class="text-lg font-bold text-surface-900">ExpenseWise</span>
      </div>

      <div class="w-full bg-white border border-surface-200 rounded-lg p-8">
        <h1 v-if="title" class="text-xl font-bold text-surface-900 mb-1">{{ title }}</h1>
        <p v-if="subtitle" class="text-sm text-surface-500 mb-5">{{ subtitle }}</p>
        <slot />
      </div>
    </div>
  </div>
</template>
