<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ password: string }>()

const strength = computed(() => {
  let score = 0
  if (props.password.length >= 8) score++
  if (/[0-9]/.test(props.password)) score++
  if (/[A-Z]/.test(props.password)) score++
  if (/[^A-Za-z0-9]/.test(props.password)) score++

  if (score <= 1) return { label: 'Weak', colorClass: 'bg-danger text-danger', pct: 33 }
  if (score <= 2) return { label: 'Fair', colorClass: 'bg-warning text-warning', pct: 66 }
  return { label: 'Strong', colorClass: 'bg-success text-success', pct: 100 }
})
</script>

<template>
  <div data-testid="password-strength-meter" class="mt-2">
    <div class="w-full h-1.5 rounded-full bg-surface-200 overflow-hidden">
      <div
        class="h-full rounded-full transition-[width] duration-base ease-out-expo"
        :class="strength.colorClass.split(' ')[0]"
        :style="{ width: strength.pct + '%' }"
      />
    </div>
    <div class="text-xs font-semibold mt-1" :class="strength.colorClass.split(' ')[1]">
      {{ strength.label }}
    </div>
  </div>
</template>
