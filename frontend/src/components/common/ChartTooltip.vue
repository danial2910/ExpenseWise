<script setup lang="ts">
import MoneyDisplay from './MoneyDisplay.vue'
import type { TooltipState } from '../../lib/chartTooltip'

defineProps<{ state: TooltipState }>()
</script>

<template>
  <Teleport to="body">
    <div
      v-if="state.visible"
      class="fixed z-50 -translate-x-1/2 -translate-y-[calc(100%+10px)] pointer-events-none bg-surface-0/95 backdrop-blur-xl border border-surface-300 rounded-lg shadow-soft-lg px-3 py-2.5 flex flex-col gap-1.5 min-w-[140px]"
      :style="{ left: state.x + 'px', top: state.y + 'px' }"
    >
      <span v-if="state.title" class="text-[11px] font-semibold text-surface-500 uppercase tracking-wide">{{ state.title }}</span>
      <div v-for="row in state.rows" :key="row.label" class="flex items-center gap-2 text-sm">
        <span class="w-2 h-2 rounded-full shrink-0" :style="{ backgroundColor: row.color }" />
        <span class="text-surface-600 flex-1">{{ row.label }}</span>
        <span class="text-surface-900 font-semibold tabular-nums"><MoneyDisplay :amount="row.amount" /></span>
      </div>
    </div>
  </Teleport>
</template>
