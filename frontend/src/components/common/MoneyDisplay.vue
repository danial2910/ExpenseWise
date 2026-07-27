<script setup lang="ts">
import { computed } from 'vue'

// Single source of truth for rendering currency (CLAUDE.md: "All currency
// renders through <MoneyDisplay>. No inline toFixed, no manual 'RM ' prefixes
// anywhere"). Jackson serializes a Java BigDecimal as a plain JSON number,
// but a string is accepted too defensively — Number() handles both the same
// way, and Intl.NumberFormat (not manual string building) gives the
// thousands separators and fixed 2-decimal formatting.
const props = withDefaults(
  defineProps<{
    amount: number | string
    // When true, prefixes a '+' for income-style positive amounts and a
    // '−' for negative ones. Color is intentionally NOT decided here — the
    // caller wraps this component in its own text-green-700/text-red-600
    // class, since "income vs expense" is a domain decision, not a
    // formatting one.
    sign?: boolean
  }>(),
  { sign: false },
)

const formatter = new Intl.NumberFormat('en-MY', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const display = computed(() => {
  const numericAmount = typeof props.amount === 'number' ? props.amount : Number(props.amount)
  const safeAmount = Number.isFinite(numericAmount) ? numericAmount : 0
  const formatted = formatter.format(Math.abs(safeAmount))
  if (!props.sign) {
    return `RM ${formatted}`
  }
  const prefix = safeAmount < 0 ? '−' : '+'
  return `${prefix} RM ${formatted}`
})
</script>

<template>
  <span>{{ display }}</span>
</template>
