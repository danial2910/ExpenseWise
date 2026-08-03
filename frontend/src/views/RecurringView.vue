<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { isAxiosError } from 'axios'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import SelectButton from 'primevue/selectbutton'
import Select from 'primevue/select'
import Dialog from 'primevue/dialog'
import Popover from 'primevue/popover'
import AppLayout from '../layouts/AppLayout.vue'
import FormError from '../components/common/FormError.vue'
import MoneyDisplay from '../components/common/MoneyDisplay.vue'
import {
  createRecurringRule,
  deleteRecurringRule,
  fetchRecurringRules,
  generateDueNow,
  patchRecurringRule,
  updateRecurringRule,
} from '../api/recurring'
import { fetchCategories } from '../api/categories'
import { categoryIconClass } from '../lib/categoryIcons'
import type { Frequency, RecurringRuleResponse } from '../types/recurring'
import type { TransactionType } from '../types/transaction'
import type { CategoryResponse } from '../types/category'
import type { ApiErrorResponse } from '../types/auth'

type LoadState = 'loading' | 'error' | 'ready'

const loadState = ref<LoadState>('loading')
const rules = ref<RecurringRuleResponse[]>([])
const categories = ref<CategoryResponse[]>([])
const listErrorMessage = ref('')

async function loadCategories() {
  categories.value = await fetchCategories()
}

async function loadRules() {
  loadState.value = 'loading'
  try {
    rules.value = await fetchRecurringRules()
    loadState.value = 'ready'
  } catch {
    loadState.value = 'error'
  }
}

async function loadAll() {
  try {
    await loadCategories()
  } catch {
    // Categories are only needed for the editor's dropdown — a failure
    // there shouldn't block the rule list itself.
  }
  await loadRules()
}

onMounted(loadAll)

function formatDate(isoDate: string) {
  return new Date(isoDate + 'T00:00:00').toLocaleDateString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  })
}
function formatShort(isoDate: string) {
  return new Date(isoDate + 'T00:00:00').toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })
}
function frequencyLabel(frequency: Frequency) {
  return frequency === 'WEEKLY' ? 'Weekly' : frequency === 'YEARLY' ? 'Yearly' : 'Monthly'
}
function rangeDisplay(rule: RecurringRuleResponse) {
  return formatShort(rule.startDate) + (rule.endDate ? ` – ${formatShort(rule.endDate)}` : ' – ongoing')
}
function typeColorClass(type: TransactionType) {
  return type === 'INCOME' ? 'text-green-700' : 'text-red-600'
}
function amountSign(type: TransactionType) {
  return type === 'INCOME' ? 1 : -1
}

const upcoming = computed(() =>
  rules.value
    .filter((r) => r.isActive)
    .slice()
    .sort((a, b) => a.nextDueDate.localeCompare(b.nextDueDate))
    .slice(0, 3),
)

// --- generate due now (demo affordance; not part of the imported design —
// see DECISIONS.md) ---
const generating = ref(false)
const generateMessage = ref('')

async function onGenerateDue() {
  generating.value = true
  generateMessage.value = ''
  try {
    const result = await generateDueNow()
    generateMessage.value =
      result.transactionsGenerated === 0
        ? 'No rules were due.'
        : `Generated ${result.transactionsGenerated} transaction(s).`
    await loadRules()
  } catch {
    generateMessage.value = 'Could not generate due transactions.'
  } finally {
    generating.value = false
  }
}

// --- row menu ---
// A PrimeVue Popover, not a hand-rolled absolutely-positioned <div>: the
// table wrapper needs overflow-hidden to keep its rounded corners, which
// was silently clipping a plain in-flow dropdown opened from the last row
// (it rendered, just entirely outside the visible box). Popover teleports
// its content to an overlay layer, so it's never subject to an ancestor's
// overflow.
const rowMenu = ref<InstanceType<typeof Popover> | null>(null)
const menuTarget = ref<RecurringRuleResponse | null>(null)
function toggleMenu(event: Event, rule: RecurringRuleResponse) {
  menuTarget.value = rule
  rowMenu.value?.toggle(event)
}
function closeMenu() {
  rowMenu.value?.hide()
}

// --- add/edit editor (right-side drawer, matching the imported design) ---
const editorOpen = ref(false)
const editingId = ref<number | null>(null)
const editType = ref<TransactionType>('EXPENSE')
const editAmount = ref('')
const editCategoryId = ref<number | null>(null)
const editDescription = ref('')
const editFrequency = ref<Frequency>('MONTHLY')
const editStartDate = ref('')
const editEndDate = ref('')
const submitted = ref(false)
const saveError = ref('')
const saving = ref(false)

const typeToggleOptions: { label: string; value: TransactionType }[] = [
  { label: 'Expense', value: 'EXPENSE' },
  { label: 'Income', value: 'INCOME' },
]
const frequencyOptions: { label: string; value: Frequency }[] = [
  { label: 'Weekly', value: 'WEEKLY' },
  { label: 'Monthly', value: 'MONTHLY' },
  { label: 'Yearly', value: 'YEARLY' },
]

const editorCategoryOptions = computed(() => categories.value.filter((c) => c.type === editType.value))
const editorTitle = computed(() => (editingId.value === null ? 'Add recurring' : 'Edit recurring'))

const amountError = computed(() => {
  if (!submitted.value) return ''
  const parsed = Number(editAmount.value)
  return !editAmount.value || Number.isNaN(parsed) || parsed <= 0 ? 'Amount must be greater than zero' : ''
})
const categoryError = computed(() => (submitted.value && editCategoryId.value === null ? 'Category is required' : ''))
const startDateError = computed(() => (submitted.value && !editStartDate.value ? 'Start date is required' : ''))
const endDateError = computed(() => {
  if (!submitted.value || !editEndDate.value) return ''
  return editEndDate.value < editStartDate.value ? 'End date must be on or after the start date' : ''
})

function todayIso() {
  return new Date().toISOString().slice(0, 10)
}

function openAdd() {
  closeMenu()
  editingId.value = null
  editType.value = 'EXPENSE'
  editAmount.value = ''
  editCategoryId.value = null
  editDescription.value = ''
  editFrequency.value = 'MONTHLY'
  editStartDate.value = todayIso()
  editEndDate.value = ''
  submitted.value = false
  saveError.value = ''
  editorOpen.value = true
}

function openEdit(rule: RecurringRuleResponse) {
  closeMenu()
  editingId.value = rule.id
  editType.value = rule.type
  editAmount.value = String(rule.amount)
  editCategoryId.value = rule.categoryId
  editDescription.value = rule.description ?? ''
  editFrequency.value = rule.frequency
  editStartDate.value = rule.startDate
  editEndDate.value = rule.endDate ?? ''
  submitted.value = false
  saveError.value = ''
  editorOpen.value = true
}

function closeEditor() {
  editorOpen.value = false
}

async function saveEditor() {
  submitted.value = true
  if (amountError.value || categoryError.value || startDateError.value || endDateError.value) {
    return
  }

  saving.value = true
  saveError.value = ''
  try {
    const request = {
      type: editType.value,
      amount: editAmount.value,
      categoryId: editCategoryId.value as number,
      description: editDescription.value.trim() || null,
      frequency: editFrequency.value,
      startDate: editStartDate.value,
      endDate: editEndDate.value || null,
    }
    if (editingId.value === null) {
      await createRecurringRule(request)
    } else {
      await updateRecurringRule(editingId.value, request)
    }
    editorOpen.value = false
    await loadRules()
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

// --- pause / resume ---
async function onTogglePause(rule: RecurringRuleResponse) {
  closeMenu()
  listErrorMessage.value = ''
  try {
    await patchRecurringRule(rule.id, { isActive: !rule.isActive })
    await loadRules()
  } catch {
    listErrorMessage.value = 'Could not update this rule.'
  }
}

// --- delete ---
const confirmOpen = ref(false)
const confirmTarget = ref<RecurringRuleResponse | null>(null)

function openConfirm(rule: RecurringRuleResponse) {
  closeMenu()
  confirmTarget.value = rule
  confirmOpen.value = true
}
function closeConfirm() {
  confirmOpen.value = false
}

async function confirmDelete() {
  if (!confirmTarget.value) return
  listErrorMessage.value = ''
  try {
    await deleteRecurringRule(confirmTarget.value.id)
    confirmOpen.value = false
    await loadRules()
  } catch {
    listErrorMessage.value = 'Could not delete this rule.'
    confirmOpen.value = false
  }
}
</script>

<template>
  <AppLayout title="Recurring">
    <div class="flex flex-col gap-6">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-surface-900">Recurring</h1>
          <p class="text-sm text-surface-500 mt-1">Automate income and expenses that repeat on a schedule</p>
        </div>
        <div class="flex items-center gap-3">
          <Button
            data-testid="generate-due-button"
            label="Generate due now"
            icon="pi pi-play"
            severity="secondary"
            outlined
            :loading="generating"
            @click.stop="onGenerateDue"
          />
          <Button data-testid="add-recurring-button" label="Add recurring" icon="pi pi-plus" @click.stop="openAdd" />
        </div>
      </div>

      <div v-if="generateMessage" data-testid="generate-due-message" class="text-sm text-surface-600">
        {{ generateMessage }}
      </div>

      <!-- error state -->
      <div
        v-if="loadState === 'error'"
        data-testid="recurring-error-state"
        class="flex flex-col items-center justify-center gap-4 py-16 px-8 bg-white border border-surface-200 rounded-lg"
      >
        <div class="w-14 h-14 rounded-lg bg-red-50 flex items-center justify-center">
          <i class="pi pi-exclamation-triangle text-red-600 text-2xl" />
        </div>
        <p class="text-base font-semibold text-surface-900">Couldn't load recurring rules</p>
        <p class="text-sm text-surface-500 text-center max-w-sm">
          Something went wrong. Check your connection and try again.
        </p>
        <Button data-testid="recurring-retry-button" label="Retry" icon="pi pi-refresh" @click="loadRules" />
      </div>

      <!-- loading state -->
      <div v-else-if="loadState === 'loading'" data-testid="recurring-loading-skeleton" class="flex flex-col gap-3">
        <div class="bg-white border border-surface-200 rounded-lg p-6 flex flex-col gap-3">
          <div v-for="n in 6" :key="n" class="w-full h-[52px] rounded-lg bg-surface-200 animate-pulse" />
        </div>
      </div>

      <!-- ready state -->
      <div v-else class="flex flex-col gap-6">
        <FormError v-if="listErrorMessage" :message="listErrorMessage" testid="recurring-list-error-banner" />

        <!-- empty state -->
        <div
          v-if="rules.length === 0"
          data-testid="recurring-empty-state"
          class="flex flex-col items-center justify-center gap-4 py-16 px-8 bg-white border border-surface-200 rounded-lg"
        >
          <div class="w-14 h-14 rounded-lg bg-surface-100 flex items-center justify-center">
            <i class="pi pi-sync text-surface-400 text-2xl" />
          </div>
          <p class="text-base font-semibold text-surface-900">No recurring rules yet</p>
          <p class="text-sm text-surface-500 text-center max-w-sm">
            Set up rent, salary, or subscriptions to auto-generate transactions on schedule.
          </p>
          <Button
            data-testid="add-first-recurring-button"
            label="Add your first recurring rule"
            icon="pi pi-plus"
            size="small"
            @click.stop="openAdd"
          />
        </div>

        <template v-else>
          <!-- due soon -->
          <div v-if="upcoming.length > 0" data-testid="recurring-due-soon" class="bg-white border border-surface-200 rounded-lg px-6 py-5">
            <p class="text-sm font-semibold text-surface-900 mb-3.5">Due soon</p>
            <div class="flex gap-4 flex-wrap">
              <div
                v-for="rule in upcoming"
                :key="rule.id"
                class="flex-1 min-w-[200px] flex items-center gap-3 px-3.5 py-3 border border-surface-100 rounded-lg bg-surface-50"
              >
                <div
                  class="w-9 h-9 rounded-lg flex items-center justify-center shrink-0"
                  :class="rule.type === 'INCOME' ? 'bg-green-50 text-green-700' : 'bg-primary-50 text-primary-600'"
                >
                  <i :class="['pi', categoryIconClass(rule.categoryIcon)]" />
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-sm font-semibold text-surface-900 truncate">{{ rule.description || rule.categoryName }}</p>
                  <p class="text-xs text-surface-500">Due {{ formatShort(rule.nextDueDate) }}</p>
                </div>
                <p class="text-sm font-semibold tabular-nums shrink-0" :class="typeColorClass(rule.type)">
                  <MoneyDisplay :amount="Number(rule.amount) * amountSign(rule.type)" sign />
                </p>
              </div>
            </div>
          </div>

          <!-- table -->
          <div data-testid="recurring-table" class="bg-white border border-surface-200 rounded-lg overflow-hidden">
            <div
              class="grid grid-cols-[1fr_100px_130px_100px_110px_110px_90px_60px] gap-3 px-5 py-3 border-b border-surface-200 bg-surface-50"
            >
              <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide">Description</span>
              <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide text-right">Amount</span>
              <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide">Category</span>
              <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide">Frequency</span>
              <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide">Next due</span>
              <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide">Start / End</span>
              <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide">Status</span>
              <span></span>
            </div>

            <div
              v-for="rule in rules"
              :key="rule.id"
              :data-testid="`recurring-row-${rule.id}`"
              class="grid grid-cols-[1fr_100px_130px_100px_110px_110px_90px_60px] gap-3 px-5 py-3.5 border-b border-surface-100 items-center"
            >
              <span class="text-sm font-medium text-surface-900 truncate pr-2">{{ rule.description || rule.categoryName }}</span>
              <span class="text-sm font-semibold text-right tabular-nums" :class="typeColorClass(rule.type)">
                <MoneyDisplay :amount="Number(rule.amount) * amountSign(rule.type)" sign />
              </span>
              <span>
                <span class="inline-flex items-center gap-1.5 text-xs font-medium text-surface-600 bg-surface-100 px-2.5 py-1 rounded-lg">
                  <i :class="['pi', categoryIconClass(rule.categoryIcon)]" />
                  {{ rule.categoryName }}
                </span>
              </span>
              <span class="text-sm text-surface-500">{{ frequencyLabel(rule.frequency) }}</span>
              <span class="text-sm text-surface-700">{{ formatShort(rule.nextDueDate) }}</span>
              <span class="text-xs text-surface-500">{{ rangeDisplay(rule) }}</span>
              <span class="flex items-center gap-1.5">
                <span class="w-1.5 h-1.5 rounded-full" :class="rule.isActive ? 'bg-green-700' : 'bg-surface-300'" />
                <span class="text-xs" :class="rule.isActive ? 'text-green-700' : 'text-surface-400'">
                  {{ rule.isActive ? 'Active' : 'Paused' }}
                </span>
              </span>
              <span class="flex justify-end">
                <button
                  :data-testid="`recurring-menu-button-${rule.id}`"
                  class="w-7 h-7 rounded-md flex items-center justify-center text-surface-500 hover:bg-surface-100"
                  @click="toggleMenu($event, rule)"
                >
                  <i class="pi pi-ellipsis-v text-xs" />
                </button>
              </span>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- Popover, not an in-flow dropdown: the table above needs
         overflow-hidden for its rounded corners, which would otherwise clip
         a menu opened from the last row entirely out of view. -->
    <Popover ref="rowMenu" data-testid="recurring-row-menu">
      <div v-if="menuTarget" class="w-36 -m-3 py-1 overflow-hidden rounded-lg">
        <button
          :data-testid="`recurring-edit-button-${menuTarget.id}`"
          class="w-full text-left px-3.5 py-2.5 text-sm text-surface-700 hover:bg-surface-50"
          @click="openEdit(menuTarget)"
        >
          Edit
        </button>
        <button
          :data-testid="`recurring-pause-button-${menuTarget.id}`"
          class="w-full text-left px-3.5 py-2.5 text-sm text-surface-700 hover:bg-surface-50"
          @click="onTogglePause(menuTarget)"
        >
          {{ menuTarget.isActive ? 'Pause' : 'Resume' }}
        </button>
        <button
          :data-testid="`recurring-delete-button-${menuTarget.id}`"
          class="w-full text-left px-3.5 py-2.5 text-sm text-red-600 hover:bg-red-50 border-t border-surface-100"
          @click="openConfirm(menuTarget)"
        >
          Delete
        </button>
      </div>
    </Popover>

    <Dialog
      v-model:visible="editorOpen"
      modal
      :header="editorTitle"
      :style="{ width: '480px' }"
      data-testid="recurring-editor-dialog"
    >
      <div class="flex flex-col gap-4">
        <FormError v-if="saveError" :message="saveError" testid="recurring-save-error-banner" />

        <SelectButton
          v-model="editType"
          data-testid="recurring-type-toggle"
          :options="typeToggleOptions"
          option-label="label"
          option-value="value"
          :allow-empty="false"
        />

        <div>
          <label for="recurring-amount-input" class="block text-xs font-semibold text-surface-600 mb-1.5">Amount</label>
          <div class="flex items-baseline gap-2 px-4 py-3 border rounded-lg bg-surface-50" :class="amountError ? 'border-red-600' : 'border-surface-200'">
            <span class="text-sm font-semibold text-surface-500">RM</span>
            <input
              id="recurring-amount-input"
              v-model="editAmount"
              data-testid="recurring-amount-input"
              type="text"
              inputmode="decimal"
              placeholder="0.00"
              class="border-none outline-none bg-transparent text-2xl font-bold text-surface-900 w-full tabular-nums"
            />
          </div>
          <p v-if="amountError" class="text-xs text-red-600 mt-1.5">{{ amountError }}</p>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div>
            <label for="recurring-start-date-input" class="block text-xs font-semibold text-surface-600 mb-1.5">Start date</label>
            <input
              id="recurring-start-date-input"
              v-model="editStartDate"
              data-testid="recurring-start-date-input"
              type="date"
              class="w-full border rounded-lg px-3 py-2 text-sm text-surface-900"
              :class="startDateError ? 'border-red-600' : 'border-surface-200'"
            />
            <p v-if="startDateError" class="text-xs text-red-600 mt-1.5">{{ startDateError }}</p>
          </div>
          <div>
            <label for="recurring-category-select" class="block text-xs font-semibold text-surface-600 mb-1.5">Category</label>
            <Select
              id="recurring-category-select"
              v-model="editCategoryId"
              data-testid="recurring-category-select"
              :options="editorCategoryOptions"
              option-label="name"
              option-value="id"
              placeholder="Select category"
              class="w-full"
              :invalid="!!categoryError"
            />
            <p v-if="categoryError" class="text-xs text-red-600 mt-1.5">{{ categoryError }}</p>
          </div>
        </div>

        <div>
          <label for="recurring-description-input" class="block text-xs font-semibold text-surface-600 mb-1.5">Description</label>
          <InputText
            id="recurring-description-input"
            v-model="editDescription"
            data-testid="recurring-description-input"
            class="w-full"
            placeholder="e.g. Monthly rent"
          />
        </div>

        <div>
          <label class="block text-xs font-semibold text-surface-600 mb-1.5">Frequency</label>
          <SelectButton
            v-model="editFrequency"
            data-testid="recurring-frequency-toggle"
            :options="frequencyOptions"
            option-label="label"
            option-value="value"
            :allow-empty="false"
          />
        </div>

        <div>
          <label for="recurring-end-date-input" class="block text-xs font-semibold text-surface-600 mb-1.5">
            End date <span class="text-surface-400 font-normal">(optional)</span>
          </label>
          <input
            id="recurring-end-date-input"
            v-model="editEndDate"
            data-testid="recurring-end-date-input"
            type="date"
            class="w-full border rounded-lg px-3 py-2 text-sm text-surface-900"
            :class="endDateError ? 'border-red-600' : 'border-surface-200'"
          />
          <p v-if="endDateError" class="text-xs text-red-600 mt-1.5">{{ endDateError }}</p>
        </div>
      </div>

      <template #footer>
        <Button data-testid="recurring-cancel-button" label="Cancel" severity="secondary" text @click="closeEditor" />
        <Button data-testid="recurring-save-button" label="Save" :loading="saving" @click="saveEditor" />
      </template>
    </Dialog>

    <Dialog
      v-model:visible="confirmOpen"
      modal
      :style="{ width: '380px' }"
      data-testid="recurring-delete-confirm-dialog"
      :show-header="false"
    >
      <div class="flex flex-col gap-4 pt-2">
        <div class="flex items-center gap-2.5">
          <i class="pi pi-exclamation-triangle text-red-600" />
          <span class="text-base font-semibold text-surface-900">Delete this rule?</span>
        </div>
        <p class="text-sm text-surface-500">
          This stops future transactions from being generated. Past transactions already created are not affected.
          This cannot be undone.
        </p>
        <div class="flex items-center justify-end gap-3">
          <Button data-testid="recurring-delete-cancel-button" label="Cancel" severity="secondary" text @click="closeConfirm" />
          <Button data-testid="recurring-delete-confirm-button" label="Delete" severity="danger" @click="confirmDelete" />
        </div>
      </div>
    </Dialog>
  </AppLayout>
</template>
