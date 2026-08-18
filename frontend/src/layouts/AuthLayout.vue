<script setup lang="ts">
import logoIcon from '../assets/logo-icon.png'

withDefaults(defineProps<{ title?: string; subtitle?: string }>(), {
  title: undefined,
  subtitle: undefined,
})
</script>

<template>
  <div class="aurora-backdrop min-h-screen bg-surface-100 flex items-center justify-center p-6">
    <div class="w-full max-w-[440px] flex flex-col items-center gap-6">
      <div class="flex items-center gap-2.5">
        <img :src="logoIcon" alt="ExpenseWise" class="w-9 h-9 object-contain" />
        <span class="font-display text-lg font-semibold tracking-tight text-surface-900">ExpenseWise</span>
      </div>

      <div class="w-full bg-surface-0/80 backdrop-blur-xl border border-surface-200 rounded-xl overflow-hidden shadow-soft-lg">
        <div class="h-0.5 bg-gradient-to-r from-aurora-emerald via-aurora-teal to-aurora-blue" />
        <div class="p-6 md:p-8">
          <h1 v-if="title" class="font-display text-xl font-semibold tracking-tight text-surface-900 mb-1">{{ title }}</h1>
          <p v-if="subtitle" class="text-sm text-surface-500 mb-5">{{ subtitle }}</p>
          <slot />
        </div>
      </div>
    </div>
  </div>
</template>
