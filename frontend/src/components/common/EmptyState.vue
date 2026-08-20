<script setup lang="ts">
// Shared "nothing here yet" block for any list/table screen. Feature pages
// will adopt this in their own redesign phase — see CLAUDE.md's Design
// system section for the source-of-truth tokens it's built from.
withDefaults(
  defineProps<{ icon?: string; title: string; description?: string; testid?: string }>(),
  { icon: 'pi-inbox', description: undefined, testid: undefined },
)
</script>

<template>
  <div
    :data-testid="testid ?? 'empty-state'"
    class="flex flex-col items-center justify-center text-center gap-3 py-16 px-6"
  >
    <div class="w-12 h-12 rounded-full bg-surface-50 border border-surface-300 flex items-center justify-center">
      <i :class="['pi', icon, 'text-xl text-surface-500']" />
    </div>
    <div class="flex flex-col gap-1">
      <p class="font-display text-base font-semibold text-surface-900">{{ title }}</p>
      <p v-if="description" class="text-sm text-surface-500 max-w-sm">{{ description }}</p>
    </div>
    <div v-if="$slots.action" class="mt-1">
      <slot name="action" />
    </div>
  </div>
</template>
