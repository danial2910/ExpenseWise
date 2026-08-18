<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { isAxiosError } from 'axios'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import AppLayout from '../layouts/AppLayout.vue'
import FormError from '../components/common/FormError.vue'
import MoneyDisplay from '../components/common/MoneyDisplay.vue'
import EmptyState from '../components/common/EmptyState.vue'
import ErrorState from '../components/common/ErrorState.vue'
import { createBudget, deleteBudget, fetchMonthBudgets, updateBudget } from '../api/budgets'
import { categoryIconClass } from '../lib/categoryIcons'
import type { BudgetMonthResponse, CategoryBudgetLine } from '../types/budget'
import type { ApiErrorResponse } from '../types/auth'

type LoadState = 'loading' | 'error' | 'ready'

const loadState = ref<LoadState>('loading')
const monthData = ref<BudgetMonthResponse | null>(null)

async function loadMonth(month?: string) {
  loadState.value = 'loading'
  try {
    monthData.value = await fetchMonthBudgets(month)
    loadState.value = 'ready'
  } catch {
    loadState.value = 'error'
  }
}

function retry() {
  loadMonth(monthData.value?.periodMonth)
}

onMounted(() => loadMonth())

const monthLabel = computed(() => {
  if (!monthData.value) return ''
  return new Date(monthData.value.periodMonth + 'T00:00:00').toLocaleDateString('en-MY', {
    month: 'short',
    year: 'numeric',
  })
})

function shiftMonth(iso: string, delta: number): string {
  const date = new Date(iso + 'T00:00:00')
  date.setMonth(date.getMonth() + delta)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  return `${year}-${month}-01`
}

function prevMonth() {
  if (!monthData.value) return
  loadMonth(shiftMonth(monthData.value.periodMonth, -1))
}
function nextMonth() {
  if (!monthData.value) return
  loadMonth(shiftMonth(monthData.value.periodMonth, 1))
}

const isEmpty = computed(() => {
  if (!monthData.value) return true
  return monthData.value.overall.budgetId === null && monthData.value.categories.every((c) => c.budgetId === null)
})

// Category budgets are capped against (and therefore require) an overall
// budget — see DECISIONS.md. The UI mirrors that: "Set budget" is disabled
// per category until an overall budget exists, and clearing the overall
// budget is disabled while any category budget still references it,
// instead of letting the user hit the backend's 409 blindly.
const hasOverallBudget = computed(() => monthData.value?.overall.budgetId !== null)
const hasAnyCategoryBudget = computed(() => monthData.value?.categories.some((c) => c.budgetId !== null) ?? false)

// Phase 4: on-track now reads as `success` (safe), not neutral — the phase
// brief explicitly asks for "safe/warning/over using semantic colors" for
// this meter, superseding the earlier "on-track is neutral, not green"
// call (see DECISIONS.md for both entries).
function statusColorClass(pct: number | null): string {
  if (pct === null) return 'text-surface-400'
  if (pct >= 100) return 'text-danger'
  if (pct >= 80) return 'text-warning'
  return 'text-success'
}
function statusBarClass(pct: number | null): string {
  if (pct === null) return 'bg-surface-300'
  if (pct >= 100) return 'bg-danger'
  if (pct >= 80) return 'bg-warning'
  return 'bg-success'
}
function statusLabel(pct: number | null): string {
  if (pct === null) return 'No limit set'
  if (pct >= 100) return 'Over budget'
  if (pct >= 80) return 'Approaching limit'
  return 'On track'
}
function clampedWidth(pct: number | string | null): number {
  if (pct === null) return 0
  return Math.min(100, Number(pct))
}
function numberOrNull(value: number | string | null): number | null {
  return value === null ? null : Number(value)
}

// --- editor (amount-only, per the design) ---
type EditorTarget = { kind: 'overall' } | { kind: 'category'; category: CategoryBudgetLine }

const editorOpen = ref(false)
const editorTarget = ref<EditorTarget | null>(null)
const editingBudgetId = ref<number | null>(null)
const editAmount = ref('')
const submitted = ref(false)
const saveError = ref('')
const saving = ref(false)

const editorTitle = computed(() => {
  if (!editorTarget.value) return ''
  return editorTarget.value.kind === 'overall'
    ? 'Set overall monthly budget'
    : `Set budget — ${editorTarget.value.category.categoryName}`
})
const amountError = computed(() => {
  if (!submitted.value) return ''
  const parsed = Number(editAmount.value)
  return !editAmount.value || Number.isNaN(parsed) || parsed <= 0 ? 'Amount must be greater than zero' : ''
})

function openOverallEdit() {
  if (!monthData.value) return
  editorTarget.value = { kind: 'overall' }
  editingBudgetId.value = monthData.value.overall.budgetId
  editAmount.value = monthData.value.overall.amount !== null ? String(monthData.value.overall.amount) : ''
  submitted.value = false
  saveError.value = ''
  editorOpen.value = true
}

function openCategoryEdit(category: CategoryBudgetLine) {
  editorTarget.value = { kind: 'category', category }
  editingBudgetId.value = category.budgetId
  editAmount.value = category.amount !== null ? String(category.amount) : ''
  submitted.value = false
  saveError.value = ''
  editorOpen.value = true
}

function closeEditor() {
  editorOpen.value = false
}

async function saveEditor() {
  submitted.value = true
  if (amountError.value || !editorTarget.value || !monthData.value) {
    return
  }

  saving.value = true
  saveError.value = ''
  try {
    const request = {
      amount: editAmount.value,
      categoryId: editorTarget.value.kind === 'category' ? editorTarget.value.category.categoryId : null,
      periodMonth: monthData.value.periodMonth,
    }
    if (editingBudgetId.value === null) {
      await createBudget(request)
    } else {
      await updateBudget(editingBudgetId.value, request)
    }
    editorOpen.value = false
    await loadMonth(monthData.value.periodMonth)
  } catch (error) {
    if (isAxiosError<ApiErrorResponse>(error) && error.response?.data.message) {
      saveError.value = error.response.data.message
    } else {
      saveError.value = 'Something went wrong. Please try again.'
    }
  } finally {
    saving.value = false
  }
}

// --- clear (delete) ---
const clearErrorId = ref<number | null>(null)
const clearErrorMessage = ref('')

async function onClear(budgetId: number) {
  clearErrorId.value = null
  clearErrorMessage.value = ''
  try {
    await deleteBudget(budgetId)
    await loadMonth(monthData.value?.periodMonth)
  } catch (error) {
    clearErrorId.value = budgetId
    if (isAxiosError<ApiErrorResponse>(error) && error.response?.data.message) {
      clearErrorMessage.value = error.response.data.message
    } else {
      clearErrorMessage.value = 'Could not clear this budget.'
    }
  }
}
</script>

<template>
  <AppLayout title="Budgets" fab-label="Set overall budget" @fab="openOverallEdit">
    <div class="flex flex-col gap-6">
      <div class="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 class="font-display text-2xl font-semibold tracking-tight text-surface-900">Budgets</h1>
          <p class="text-sm text-surface-500 mt-1">Plan and track spending limits by category</p>
        </div>
        <div v-if="monthData" class="flex items-center gap-2 bg-surface-0 border border-surface-200 rounded-lg px-2 py-1.5 self-start">
          <button
            data-testid="budget-prev-month-button"
            class="w-11 h-11 lg:w-7 lg:h-7 rounded-md flex items-center justify-center text-surface-600 hover:bg-surface-50 transition-colors duration-fast ease-out-expo"
            @click="prevMonth"
          >
            <i class="pi pi-chevron-left text-xs" />
          </button>
          <span data-testid="budget-month-label" class="text-sm font-semibold text-surface-900 min-w-[90px] text-center">
            {{ monthLabel }}
          </span>
          <button
            data-testid="budget-next-month-button"
            class="w-11 h-11 lg:w-7 lg:h-7 rounded-md flex items-center justify-center text-surface-600 hover:bg-surface-50 transition-colors duration-fast ease-out-expo"
            @click="nextMonth"
          >
            <i class="pi pi-chevron-right text-xs" />
          </button>
        </div>
      </div>

      <ErrorState
        v-if="loadState === 'error'"
        testid="budgets-error-state"
        retry-testid="budgets-retry-button"
        title="Couldn't load budgets"
        description="Something went wrong while fetching your budgets. Check your connection and try again."
        class="bg-surface-0 border border-surface-200 rounded-xl"
        @retry="retry"
      />

      <!-- loading state -->
      <div v-else-if="loadState === 'loading'" data-testid="budgets-loading-skeleton" class="flex flex-col gap-4">
        <div class="bg-surface-0 border border-surface-200 rounded-xl p-6 h-24 animate-pulse" />
        <div class="bg-surface-0 border border-surface-200 rounded-xl overflow-hidden">
          <div v-for="n in 6" :key="n" class="p-4 border-b border-surface-100 flex items-center gap-4">
            <div class="w-28 h-3.5 rounded bg-surface-200 animate-pulse" />
            <div class="flex-1 h-3.5 rounded bg-surface-200 animate-pulse" />
          </div>
        </div>
      </div>

      <!-- ready state -->
      <div v-else class="flex flex-col gap-6">
        <div v-if="clearErrorMessage">
          <FormError :message="clearErrorMessage" testid="budget-clear-error-banner" />
        </div>

        <EmptyState
          v-if="isEmpty"
          testid="budgets-empty-state"
          icon="pi-wallet"
          :title="`No budgets set for ${monthLabel}`"
          description="Set a monthly limit overall or per category to see how your spending tracks against it."
          class="bg-surface-0 border border-surface-200 rounded-xl"
        >
          <template #action>
            <Button
              data-testid="set-overall-budget-button"
              label="Set overall budget"
              icon="pi pi-plus"
              class="active:scale-[0.98] transition-transform duration-fast ease-out-expo"
              @click="openOverallEdit"
            />
          </template>
        </EmptyState>

        <template v-else>
          <!-- overall budget card -->
          <div data-testid="overall-budget-card" class="bg-surface-0 border border-surface-200 rounded-xl p-4 md:p-6">
            <!-- desktop/tablet -->
            <div class="hidden md:block">
              <div class="flex items-center justify-between mb-4">
                <span class="text-sm font-semibold text-surface-900">Overall Monthly Budget</span>
                <div class="flex items-center gap-4">
                  <button
                    data-testid="overall-budget-edit-button"
                    class="text-xs font-semibold text-primary-600"
                    @click="openOverallEdit"
                  >
                    {{ monthData!.overall.budgetId === null ? 'Set budget' : 'Edit' }}
                  </button>
                  <button
                    v-if="monthData!.overall.budgetId !== null"
                    data-testid="overall-budget-clear-button"
                    class="text-xs font-semibold"
                    :class="hasAnyCategoryBudget ? 'text-surface-300 cursor-not-allowed' : 'text-surface-500 hover:text-danger'"
                    :disabled="hasAnyCategoryBudget"
                    :title="hasAnyCategoryBudget ? 'Clear this month\'s category budgets first' : undefined"
                    @click="!hasAnyCategoryBudget && onClear(monthData!.overall.budgetId!)"
                  >
                    Clear
                  </button>
                </div>
              </div>
              <div class="flex items-baseline gap-6 mb-3">
                <div>
                  <p class="text-[11px] font-semibold text-surface-500 uppercase tracking-wide">Budget</p>
                  <p class="text-xl font-bold text-surface-900 tabular-nums">
                    <MoneyDisplay v-if="monthData!.overall.amount !== null" :amount="monthData!.overall.amount" />
                    <span v-else class="text-surface-400 text-base font-semibold">Not set</span>
                  </p>
                </div>
                <div>
                  <p class="text-[11px] font-semibold text-surface-500 uppercase tracking-wide">Spent</p>
                  <p class="text-xl font-bold text-surface-900 tabular-nums">
                    <MoneyDisplay :amount="monthData!.overall.spent" />
                  </p>
                </div>
                <div>
                  <p class="text-[11px] font-semibold text-surface-500 uppercase tracking-wide">Remaining</p>
                  <p
                    class="text-xl font-bold tabular-nums font-display"
                    :class="monthData!.overall.remaining !== null && Number(monthData!.overall.remaining) < 0 ? 'text-danger' : 'text-surface-900'"
                  >
                    <MoneyDisplay
                      v-if="monthData!.overall.remaining !== null"
                      :amount="Math.abs(Number(monthData!.overall.remaining))"
                    />
                    <span v-else class="text-surface-400 text-base font-semibold">—</span>
                  </p>
                </div>
              </div>
              <div class="w-full h-2.5 rounded-full bg-surface-200 overflow-hidden">
                <div
                  class="h-full rounded-full"
                  :class="statusBarClass(numberOrNull(monthData!.overall.progressPercent))"
                  :style="{ width: clampedWidth(monthData!.overall.progressPercent) + '%' }"
                />
              </div>
              <p class="text-xs font-semibold mt-2" :class="statusColorClass(numberOrNull(monthData!.overall.progressPercent))">
                {{ statusLabel(numberOrNull(monthData!.overall.progressPercent)) }}
              </p>
            </div>

            <!-- mobile -->
            <div class="md:hidden">
              <div class="flex items-center justify-between mb-2.5">
                <span class="text-sm font-semibold text-surface-900">Overall Budget</span>
                <div class="flex items-center gap-3">
                  <button
                    data-testid="overall-budget-edit-button-mobile"
                    class="min-h-11 flex items-center text-sm font-semibold text-primary-600"
                    @click="openOverallEdit"
                  >
                    {{ monthData!.overall.budgetId === null ? 'Set budget' : 'Edit' }}
                  </button>
                  <button
                    v-if="monthData!.overall.budgetId !== null"
                    data-testid="overall-budget-clear-button-mobile"
                    class="min-h-11 flex items-center text-xs font-semibold"
                    :class="hasAnyCategoryBudget ? 'text-surface-300' : 'text-surface-500'"
                    :disabled="hasAnyCategoryBudget"
                    @click="!hasAnyCategoryBudget && onClear(monthData!.overall.budgetId!)"
                  >
                    Clear
                  </button>
                </div>
              </div>
              <p class="text-xs font-semibold mb-2.5" :class="statusColorClass(numberOrNull(monthData!.overall.progressPercent))">
                <MoneyDisplay :amount="monthData!.overall.spent" /> of
                <template v-if="monthData!.overall.amount !== null"><MoneyDisplay :amount="monthData!.overall.amount" /></template>
                <span v-else>Not set</span>
                — {{ statusLabel(numberOrNull(monthData!.overall.progressPercent)) }}
              </p>
              <div class="w-full h-2.5 rounded-full bg-surface-200 overflow-hidden">
                <div
                  class="h-full rounded-full"
                  :class="statusBarClass(numberOrNull(monthData!.overall.progressPercent))"
                  :style="{ width: clampedWidth(monthData!.overall.progressPercent) + '%' }"
                />
              </div>
            </div>
          </div>

          <!-- per-category table -->
          <div data-testid="budgets-table" class="bg-surface-0 border border-surface-200 rounded-xl overflow-hidden">
            <div class="hidden md:grid grid-cols-[1fr_120px_120px_120px_90px_140px_110px] px-5 py-3 border-b border-surface-200 bg-surface-50">
              <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide">Category</span>
              <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide text-right">Budget</span>
              <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide text-right">Spent</span>
              <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide text-right">Remaining</span>
              <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide text-right">Used</span>
              <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide pl-4">Progress</span>
              <span></span>
            </div>

            <div
              v-for="category in monthData!.categories"
              :key="category.categoryId"
              :data-testid="`budget-row-${category.categoryId}`"
              class="border-b border-surface-100"
            >
              <!-- desktop/tablet row -->
              <div class="hidden md:grid grid-cols-[1fr_120px_120px_120px_90px_140px_110px] px-5 py-3.5 items-center">
                <span class="text-sm font-medium text-surface-900 flex items-center gap-2">
                  <i :class="['pi', categoryIconClass(category.categoryIcon)]" class="text-surface-400" />
                  {{ category.categoryName }}
                </span>

                <template v-if="category.budgetId !== null">
                  <span class="text-sm text-surface-700 text-right tabular-nums">
                    <MoneyDisplay :amount="category.amount!" />
                  </span>
                  <span class="text-sm text-surface-700 text-right tabular-nums">
                    <MoneyDisplay :amount="category.spent" />
                  </span>
                  <span
                    class="text-sm text-right tabular-nums"
                    :class="Number(category.remaining) < 0 ? 'text-danger' : 'text-surface-700'"
                  >
                    <MoneyDisplay :amount="Math.abs(Number(category.remaining))" />
                  </span>
                  <span class="text-sm font-semibold text-right" :class="statusColorClass(numberOrNull(category.progressPercent))">
                    {{ Math.round(Number(category.progressPercent)) }}%
                  </span>
                  <span class="pl-4">
                    <span class="block w-full h-1.5 rounded-full bg-surface-200 overflow-hidden">
                      <span
                        class="block h-full rounded-full"
                        :class="statusBarClass(numberOrNull(category.progressPercent))"
                        :style="{ width: clampedWidth(category.progressPercent) + '%' }"
                      />
                    </span>
                  </span>
                  <span class="flex items-center justify-end gap-3">
                    <button
                      :data-testid="`budget-edit-button-${category.categoryId}`"
                      class="text-xs font-semibold text-primary-600"
                      @click="openCategoryEdit(category)"
                    >
                      Edit
                    </button>
                    <button
                      :data-testid="`budget-clear-button-${category.categoryId}`"
                      class="text-xs font-semibold text-surface-500 hover:text-danger transition-colors duration-fast ease-out-expo"
                      @click="onClear(category.budgetId!)"
                    >
                      Clear
                    </button>
                  </span>
                </template>
                <template v-else>
                  <span class="col-span-4 text-sm text-surface-400">
                    No budget set · <MoneyDisplay :amount="category.spent" /> spent
                  </span>
                  <span></span>
                  <span class="flex items-center justify-end">
                    <button
                      v-if="hasOverallBudget"
                      :data-testid="`budget-set-button-${category.categoryId}`"
                      class="text-xs font-semibold text-primary-600 whitespace-nowrap"
                      @click="openCategoryEdit(category)"
                    >
                      Set budget
                    </button>
                    <span
                      v-else
                      :data-testid="`budget-set-button-${category.categoryId}`"
                      class="text-xs font-semibold text-surface-300 whitespace-nowrap cursor-not-allowed"
                      title="Set an overall budget for this month first"
                    >
                      Set budget
                    </span>
                  </span>
                </template>
              </div>

              <!-- mobile card -->
              <div class="md:hidden flex flex-col gap-2 px-4 py-3.5">
                <div class="flex items-center justify-between gap-2">
                  <span class="text-sm font-medium text-surface-900 flex items-center gap-2 min-w-0">
                    <i :class="['pi', categoryIconClass(category.categoryIcon)]" class="text-surface-400 shrink-0" />
                    <span class="truncate">{{ category.categoryName }}</span>
                  </span>
                  <div class="flex items-center gap-3 shrink-0">
                    <button
                      v-if="category.budgetId !== null"
                      :data-testid="`mobile-budget-edit-button-${category.categoryId}`"
                      class="min-h-11 flex items-center text-xs font-semibold text-primary-600"
                      @click="openCategoryEdit(category)"
                    >
                      Edit
                    </button>
                    <button
                      v-else-if="hasOverallBudget"
                      :data-testid="`mobile-budget-set-button-${category.categoryId}`"
                      class="min-h-11 flex items-center text-xs font-semibold text-primary-600 whitespace-nowrap"
                      @click="openCategoryEdit(category)"
                    >
                      Set budget
                    </button>
                    <span
                      v-else
                      :data-testid="`mobile-budget-set-button-${category.categoryId}`"
                      class="min-h-11 flex items-center text-xs font-semibold text-surface-300 whitespace-nowrap"
                    >
                      Set budget
                    </span>
                  </div>
                </div>

                <template v-if="category.budgetId !== null">
                  <div class="flex items-center justify-between">
                    <span class="text-xs text-surface-500">
                      <MoneyDisplay :amount="category.spent" /> of <MoneyDisplay :amount="category.amount!" />
                    </span>
                    <span class="text-xs font-semibold" :class="statusColorClass(numberOrNull(category.progressPercent))">
                      {{ Math.round(Number(category.progressPercent)) }}%
                    </span>
                  </div>
                  <div class="w-full h-1.5 rounded-full bg-surface-200 overflow-hidden">
                    <div
                      class="h-full rounded-full"
                      :class="statusBarClass(numberOrNull(category.progressPercent))"
                      :style="{ width: clampedWidth(category.progressPercent) + '%' }"
                    />
                  </div>
                  <button
                    :data-testid="`mobile-budget-clear-button-${category.categoryId}`"
                    class="self-start min-h-11 text-xs font-semibold text-surface-500"
                    @click="onClear(category.budgetId!)"
                  >
                    Clear
                  </button>
                </template>
                <template v-else>
                  <span class="text-xs text-surface-400">
                    No budget set · <MoneyDisplay :amount="category.spent" /> spent
                  </span>
                </template>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>

    <Dialog
      v-model:visible="editorOpen"
      modal
      :header="editorTitle"
      :style="{ width: '420px' }"
      :breakpoints="{ '768px': '92vw' }"
      data-testid="budget-editor-dialog"
    >
      <div class="flex flex-col gap-4">
        <FormError v-if="saveError" :message="saveError" testid="budget-save-error-banner" />

        <div>
          <label for="budget-amount-input" class="block text-xs font-semibold text-surface-600 mb-1.5">Monthly limit</label>
          <div
            class="flex items-baseline gap-2 px-4 py-3 border rounded-lg bg-surface-50 focus-within:ring-2 focus-within:ring-primary-500 focus-within:ring-offset-2 focus-within:ring-offset-surface-100"
            :class="amountError ? 'border-danger' : 'border-surface-200'"
          >
            <span class="text-sm font-semibold text-surface-500">RM</span>
            <input
              id="budget-amount-input"
              v-model="editAmount"
              data-testid="budget-amount-input"
              type="text"
              inputmode="decimal"
              placeholder="0.00"
              class="border-none outline-none bg-transparent text-2xl font-bold text-surface-900 w-full tabular-nums"
            />
          </div>
          <p v-if="amountError" class="text-xs text-danger mt-1.5">{{ amountError }}</p>
        </div>
      </div>

      <template #footer>
        <Button data-testid="budget-cancel-button" label="Cancel" severity="secondary" text @click="closeEditor" />
        <Button
          data-testid="budget-save-button"
          label="Save"
          :loading="saving"
          class="active:scale-[0.98] transition-transform duration-fast ease-out-expo"
          @click="saveEditor"
        />
      </template>
    </Dialog>
  </AppLayout>
</template>
