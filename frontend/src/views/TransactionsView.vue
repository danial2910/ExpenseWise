<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isAxiosError } from 'axios'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import SelectButton from 'primevue/selectbutton'
import Select from 'primevue/select'
import Dialog from 'primevue/dialog'
import AppLayout from '../layouts/AppLayout.vue'
import FormError from '../components/common/FormError.vue'
import MoneyDisplay from '../components/common/MoneyDisplay.vue'
import EmptyState from '../components/common/EmptyState.vue'
import ErrorState from '../components/common/ErrorState.vue'
import {
  createTransaction,
  deleteTransaction,
  fetchSummary,
  fetchTransactions,
  removeReceipt,
  updateTransaction,
  uploadReceipt,
} from '../api/transactions'
import { fetchCategories } from '../api/categories'
import { categoryIconClass } from '../lib/categoryIcons'
import type { TransactionResponse, TransactionSummaryResponse, TransactionType } from '../types/transaction'
import type { CategoryResponse } from '../types/category'
import type { ApiErrorResponse } from '../types/auth'

type LoadState = 'loading' | 'error' | 'ready'
type TypeFilter = 'ALL' | TransactionType

const PAGE_SIZE = 20

const loadState = ref<LoadState>('loading')
const transactions = ref<TransactionResponse[]>([])
const summary = ref<TransactionSummaryResponse | null>(null)
const categories = ref<CategoryResponse[]>([])
const page = ref(0)
const totalPages = ref(0)

// --- filters ---
const typeFilterOptions: { label: string; value: TypeFilter }[] = [
  { label: 'All', value: 'ALL' },
  { label: 'Income', value: 'INCOME' },
  { label: 'Expense', value: 'EXPENSE' },
]
const typeFilter = ref<TypeFilter>('ALL')
const categoryFilter = ref<number | null>(null)
const dateFrom = ref('')
const dateTo = ref('')
const search = ref('')

const categoryFilterOptions = computed(() => [
  { id: null, name: 'All categories' },
  ...categories.value,
])

function filterParams() {
  return {
    type: typeFilter.value === 'ALL' ? undefined : typeFilter.value,
    categoryId: categoryFilter.value ?? undefined,
    from: dateFrom.value || undefined,
    to: dateTo.value || undefined,
    search: search.value || undefined,
  }
}

async function loadCategories() {
  categories.value = await fetchCategories()
}

async function loadTransactions() {
  loadState.value = 'loading'
  try {
    const [transactionsPage, summaryResponse] = await Promise.all([
      fetchTransactions({ ...filterParams(), page: page.value, size: PAGE_SIZE, sort: 'transactionDate,desc' }),
      fetchSummary(filterParams()),
    ])
    transactions.value = transactionsPage.content
    totalPages.value = transactionsPage.totalPages
    summary.value = summaryResponse
    loadState.value = 'ready'
  } catch {
    loadState.value = 'error'
  }
}

async function loadAll() {
  try {
    await loadCategories()
  } catch {
    // Categories are only needed for the filter/editor dropdowns — a
    // failure there shouldn't block the transactions list itself.
  }
  await loadTransactions()
}

const route = useRoute()
const router = useRouter()

onMounted(async () => {
  await loadAll()
  // Dashboard's mobile FAB links here with ?add=1 to jump straight into the
  // add-transaction dialog, since there's no standalone "add" page/route.
  if (route.query.add === '1') {
    openAdd()
    router.replace({ query: {} })
  }
})

watch([typeFilter, categoryFilter, dateFrom, dateTo], () => {
  page.value = 0
  loadTransactions()
})

let searchDebounce: ReturnType<typeof setTimeout> | undefined
watch(search, () => {
  clearTimeout(searchDebounce)
  searchDebounce = setTimeout(() => {
    page.value = 0
    loadTransactions()
  }, 300)
})

const hasActiveFilters = computed(() =>
  typeFilter.value !== 'ALL' ||
  categoryFilter.value !== null ||
  dateFrom.value !== '' ||
  dateTo.value !== '' ||
  search.value !== '',
)

function resetFilters() {
  clearTimeout(searchDebounce)
  typeFilter.value = 'ALL'
  categoryFilter.value = null
  dateFrom.value = ''
  dateTo.value = ''
  search.value = ''
  page.value = 0
  loadTransactions()
}

function prevPage() {
  if (page.value > 0) {
    page.value -= 1
    loadTransactions()
  }
}
function nextPage() {
  if (page.value + 1 < totalPages.value) {
    page.value += 1
    loadTransactions()
  }
}

function typeColorClass(type: TransactionType) {
  return type === 'INCOME' ? 'text-success' : 'text-danger'
}
function typePillClass(type: TransactionType) {
  return type === 'INCOME' ? 'bg-success-bg text-success' : 'bg-danger-bg text-danger'
}
function amountSign(type: TransactionType) {
  return type === 'INCOME' ? 1 : -1
}
function formatDate(isoDate: string) {
  return new Date(isoDate + 'T00:00:00').toLocaleDateString('en-MY', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

// --- add/edit editor ---
const editorOpen = ref(false)
const editingId = ref<number | null>(null)
const editType = ref<TransactionType>('EXPENSE')
const editAmount = ref('')
const editCategoryId = ref<number | null>(null)
const editDate = ref('')
const editDescription = ref('')
const submitted = ref(false)
const saveError = ref('')
const saving = ref(false)

const typeToggleOptions: { label: string; value: TransactionType }[] = [
  { label: 'Expense', value: 'EXPENSE' },
  { label: 'Income', value: 'INCOME' },
]

const editorCategoryOptions = computed(() => categories.value.filter((c) => c.type === editType.value))

const editorTitle = computed(() => (editingId.value === null ? 'Add transaction' : 'Edit transaction'))
const amountError = computed(() => {
  if (!submitted.value) return ''
  const parsed = Number(editAmount.value)
  return !editAmount.value || Number.isNaN(parsed) || parsed <= 0 ? 'Amount must be greater than zero' : ''
})
const categoryError = computed(() => (submitted.value && editCategoryId.value === null ? 'Category is required' : ''))
const dateError = computed(() => (submitted.value && !editDate.value ? 'Date is required' : ''))

function todayIso() {
  return new Date().toISOString().slice(0, 10)
}

function openAdd() {
  editingId.value = null
  editType.value = 'EXPENSE'
  editAmount.value = ''
  editCategoryId.value = null
  editDate.value = todayIso()
  editDescription.value = ''
  submitted.value = false
  saveError.value = ''
  editReceiptUrl.value = null
  receiptError.value = ''
  pendingReceiptFile.value = null
  editorOpen.value = true
}

function openEdit(transaction: TransactionResponse) {
  editingId.value = transaction.id
  editType.value = transaction.type
  editAmount.value = String(transaction.amount)
  editCategoryId.value = transaction.categoryId
  editDate.value = transaction.transactionDate
  editDescription.value = transaction.description ?? ''
  submitted.value = false
  saveError.value = ''
  editReceiptUrl.value = transaction.receiptUrl
  receiptError.value = ''
  pendingReceiptFile.value = null
  editorOpen.value = true
}

function closeEditor() {
  editorOpen.value = false
}

// --- receipt ---
// When adding a new transaction there is no id yet (receipts.transaction_id
// is a NOT NULL FK — see V1__baseline.sql), so the picked file is staged
// locally in pendingReceiptFile and only actually uploaded once
// createTransaction() returns a real id, inside saveEditor().
const editReceiptUrl = ref<string | null>(null)
const pendingReceiptFile = ref<File | null>(null)
const receiptUploading = ref(false)
const receiptError = ref('')
const receiptFileInputRef = ref<HTMLInputElement | null>(null)

function triggerReceiptPicker() {
  receiptFileInputRef.value?.click()
}

function applyUpdatedTransaction(updated: TransactionResponse) {
  const index = transactions.value.findIndex((t) => t.id === updated.id)
  if (index !== -1) {
    transactions.value[index] = updated
  }
  editReceiptUrl.value = updated.receiptUrl
}

async function onReceiptSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  receiptError.value = ''

  if (editingId.value === null) {
    pendingReceiptFile.value = file
    return
  }

  receiptUploading.value = true
  try {
    const updated = await uploadReceipt(editingId.value, file)
    applyUpdatedTransaction(updated)
  } catch (error) {
    if (isAxiosError<ApiErrorResponse>(error) && error.response?.data.message) {
      receiptError.value = error.response.data.message
    } else {
      receiptError.value = 'Could not upload the receipt. Please try again.'
    }
  } finally {
    receiptUploading.value = false
  }
}

function clearPendingReceipt() {
  pendingReceiptFile.value = null
}

async function onRemoveReceipt() {
  if (editingId.value === null) return
  receiptUploading.value = true
  receiptError.value = ''
  try {
    const updated = await removeReceipt(editingId.value)
    applyUpdatedTransaction(updated)
  } catch {
    receiptError.value = 'Could not remove the receipt. Please try again.'
  } finally {
    receiptUploading.value = false
  }
}

// Switching type invalidates a category chosen under the other type.
watch(editType, () => {
  if (!editorCategoryOptions.value.some((c) => c.id === editCategoryId.value)) {
    editCategoryId.value = null
  }
})

async function saveEditor() {
  submitted.value = true
  if (amountError.value || categoryError.value || dateError.value) {
    return
  }

  saving.value = true
  saveError.value = ''
  try {
    const request = {
      type: editType.value,
      amount: editAmount.value,
      categoryId: editCategoryId.value as number,
      transactionDate: editDate.value,
      description: editDescription.value.trim() || null,
    }
    const isCreating = editingId.value === null
    const saved = isCreating ? await createTransaction(request) : await updateTransaction(editingId.value as number, request)

    if (isCreating && pendingReceiptFile.value) {
      // The transaction now has a real id, so the staged file can finally be
      // uploaded. Switch the editor into edit mode for this id so that, if
      // the upload fails, the user can retry it from the normal receipt UI
      // instead of losing the file picked at create time.
      editingId.value = saved.id
      try {
        receiptUploading.value = true
        await uploadReceipt(saved.id, pendingReceiptFile.value)
      } catch (error) {
        receiptError.value = isAxiosError<ApiErrorResponse>(error) && error.response?.data.message
          ? error.response.data.message
          : 'Transaction was created, but the receipt could not be uploaded. Try again below.'
        page.value = 0
        await loadTransactions()
        return
      } finally {
        receiptUploading.value = false
        pendingReceiptFile.value = null
      }
    }

    editorOpen.value = false
    page.value = 0
    await loadTransactions()
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

// --- delete ---
const deleteErrorId = ref<number | null>(null)
const deleteErrorMessage = ref('')

async function onDelete(transaction: TransactionResponse) {
  deleteErrorId.value = null
  deleteErrorMessage.value = ''
  try {
    await deleteTransaction(transaction.id)
    await loadTransactions()
  } catch {
    deleteErrorId.value = transaction.id
    deleteErrorMessage.value = 'Could not delete this transaction.'
  }
}
</script>

<template>
  <AppLayout title="Transactions" fab-label="Add transaction" @fab="openAdd">
    <div class="flex flex-col gap-6">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="font-display text-2xl font-semibold tracking-tight text-surface-900">Transactions</h1>
          <p class="text-sm text-surface-500 mt-1">All your income and expenses in one place</p>
        </div>
        <Button
          data-testid="add-transaction-button"
          label="Add transaction"
          icon="pi pi-plus"
          class="hidden md:inline-flex active:scale-[0.98] transition-transform duration-fast ease-out-expo"
          @click="openAdd"
        />
      </div>

      <!-- filters -->
      <div class="flex items-center gap-3 flex-wrap">
        <SelectButton
          v-model="typeFilter"
          data-testid="transaction-type-filter"
          :options="typeFilterOptions"
          option-label="label"
          option-value="value"
          :allow-empty="false"
        />
        <Select
          v-model="categoryFilter"
          data-testid="transaction-category-filter"
          :options="categoryFilterOptions"
          option-label="name"
          option-value="id"
          placeholder="All categories"
          class="min-w-[180px]"
        />
        <input
          v-model="dateFrom"
          type="date"
          data-testid="transaction-date-from-input"
          class="border border-surface-300 rounded-lg px-3 py-2 text-sm text-surface-900 bg-surface-0"
        />
        <input
          v-model="dateTo"
          type="date"
          data-testid="transaction-date-to-input"
          class="border border-surface-300 rounded-lg px-3 py-2 text-sm text-surface-900 bg-surface-0"
        />
        <IconField class="flex-1 min-w-[220px] max-w-[320px]">
          <InputIcon class="pi pi-search" />
          <InputText
            v-model="search"
            data-testid="transaction-search-input"
            class="w-full"
            placeholder="Search description or category"
          />
        </IconField>
        <Button
          data-testid="transaction-reset-filters-button"
          label="Reset filters"
          icon="pi pi-refresh"
          severity="secondary"
          text
          :disabled="!hasActiveFilters"
          @click="resetFilters"
        />
      </div>

      <!-- summary strip -->
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div class="bg-surface-0 border border-surface-300 rounded-xl p-4">
          <span class="block h-[3px] w-6 rounded-full bg-success" />
          <p class="text-xs font-medium text-surface-500 mt-2.5">Total income</p>
          <p data-testid="summary-total-income" class="text-2xl font-bold text-success mt-1.5 tabular-nums font-display">
            <MoneyDisplay v-if="summary" :amount="summary.totalIncome" sign />
            <span v-else>&nbsp;</span>
          </p>
        </div>
        <div class="bg-surface-0 border border-surface-300 rounded-xl p-4">
          <span class="block h-[3px] w-6 rounded-full bg-danger" />
          <p class="text-xs font-medium text-surface-500 mt-2.5">Total expenses</p>
          <p data-testid="summary-total-expense" class="text-2xl font-bold text-danger mt-1.5 tabular-nums font-display">
            <template v-if="summary">− <MoneyDisplay :amount="summary.totalExpense" /></template>
            <span v-else>&nbsp;</span>
          </p>
        </div>
        <div class="bg-surface-0 border border-surface-300 rounded-xl p-4">
          <span class="block h-[3px] w-6 rounded-full bg-primary-500" />
          <p class="text-xs font-medium text-surface-500 mt-2.5">Balance</p>
          <p
            data-testid="summary-balance"
            class="text-2xl font-bold mt-1.5 tabular-nums font-display"
            :class="summary && Number(summary.balance) < 0 ? 'text-danger' : 'text-success'"
          >
            <MoneyDisplay v-if="summary" :amount="summary.balance" sign />
            <span v-else>&nbsp;</span>
          </p>
        </div>
      </div>

      <ErrorState
        v-if="loadState === 'error'"
        testid="transactions-error-state"
        retry-testid="transactions-retry-button"
        title="Couldn't load transactions"
        description="Something went wrong while fetching your transactions. Check your connection and try again."
        class="bg-surface-0 border border-surface-300 rounded-xl"
        @retry="loadTransactions"
      />

      <!-- loading state -->
      <div v-else-if="loadState === 'loading'" data-testid="transactions-loading-skeleton" class="flex flex-col gap-2">
        <div v-for="n in 8" :key="n" class="bg-surface-0 border border-surface-300 rounded-xl p-4 flex items-center gap-4">
          <div class="w-16 h-3.5 rounded bg-surface-200 animate-pulse shrink-0" />
          <div class="flex-1 h-3.5 rounded bg-surface-200 animate-pulse" />
          <div class="w-24 h-5 rounded-full bg-surface-200 animate-pulse shrink-0" />
          <div class="w-20 h-3.5 rounded bg-surface-200 animate-pulse shrink-0" />
        </div>
      </div>

      <!-- ready state -->
      <div v-else class="flex flex-col gap-4">
        <div v-if="deleteErrorMessage">
          <FormError :message="deleteErrorMessage" testid="transaction-delete-error-banner" />
        </div>

        <EmptyState
          v-if="transactions.length === 0"
          testid="transactions-empty-state"
          icon="pi-list"
          title="No transactions yet"
          description="Add your first income or expense to start tracking your spending and see it show up here."
          class="bg-surface-0 border border-dashed border-surface-300 rounded-xl"
        >
          <template #action>
            <Button
              data-testid="add-first-transaction-button"
              label="Add your first transaction"
              icon="pi pi-plus"
              size="small"
              class="active:scale-[0.98] transition-transform duration-fast ease-out-expo"
              @click="openAdd"
            />
          </template>
        </EmptyState>

        <div v-else data-testid="transactions-table" class="bg-surface-0 border border-surface-300 rounded-xl overflow-hidden">
          <div
            class="hidden md:grid grid-cols-[110px_1fr_140px_100px_140px_72px] px-5 py-3 border-b border-surface-200 bg-surface-50"
          >
            <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide">Date</span>
            <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide">Description</span>
            <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide">Category</span>
            <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide">Type</span>
            <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide text-right">Amount</span>
            <span></span>
          </div>

          <div
            v-for="transaction in transactions"
            :key="transaction.id"
            :data-testid="`transaction-row-${transaction.id}`"
            class="border-b border-surface-100"
          >
            <!-- desktop/tablet row -->
            <div class="hidden md:grid grid-cols-[110px_1fr_140px_100px_140px_72px] px-5 py-3.5 items-center">
              <span class="text-sm text-surface-500">{{ formatDate(transaction.transactionDate) }}</span>
              <span class="text-sm font-medium text-surface-900 truncate pr-3 flex items-center gap-1.5">
                {{ transaction.description || '—' }}
                <a
                  v-if="transaction.receiptUrl"
                  :data-testid="`transaction-receipt-link-${transaction.id}`"
                  :href="transaction.receiptUrl"
                  target="_blank"
                  rel="noopener"
                  class="text-surface-400 hover:text-primary-300 shrink-0 transition-colors duration-fast ease-out-expo"
                  title="View receipt"
                  @click.stop
                >
                  <i class="pi pi-paperclip text-xs" />
                </a>
              </span>
              <span>
                <span class="inline-flex items-center gap-1.5 text-xs font-medium text-surface-600 bg-surface-100 px-2.5 py-1 rounded-lg">
                  <i :class="['pi', categoryIconClass(transaction.categoryIcon)]" />
                  {{ transaction.categoryName }}
                </span>
              </span>
              <span>
                <span class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold" :class="typePillClass(transaction.type)">
                  {{ transaction.type === 'INCOME' ? 'Income' : 'Expense' }}
                </span>
              </span>
              <span class="text-sm font-semibold text-right tabular-nums" :class="typeColorClass(transaction.type)">
                <MoneyDisplay :amount="Number(transaction.amount) * amountSign(transaction.type)" sign />
              </span>
              <span class="flex items-center justify-end gap-1">
                <button
                  :data-testid="`transaction-edit-button-${transaction.id}`"
                  class="w-7 h-7 rounded-md flex items-center justify-center text-surface-500 hover:bg-surface-50 hover:text-surface-800 transition-colors duration-fast ease-out-expo"
                  @click="openEdit(transaction)"
                >
                  <i class="pi pi-pencil text-xs" />
                </button>
                <button
                  :data-testid="`transaction-delete-button-${transaction.id}`"
                  class="w-7 h-7 rounded-md flex items-center justify-center text-surface-500 hover:bg-danger-bg hover:text-danger transition-colors duration-fast ease-out-expo"
                  @click="onDelete(transaction)"
                >
                  <i class="pi pi-trash text-xs" />
                </button>
              </span>
            </div>

            <!-- mobile card -->
            <div class="md:hidden flex flex-col gap-2 px-4 py-3.5" @click="openEdit(transaction)">
              <div class="flex items-center justify-between gap-3">
                <span class="text-sm font-medium text-surface-900 truncate flex items-center gap-1.5">
                  {{ transaction.description || '—' }}
                  <a
                    v-if="transaction.receiptUrl"
                    :data-testid="`mobile-transaction-receipt-link-${transaction.id}`"
                    :href="transaction.receiptUrl"
                    target="_blank"
                    rel="noopener"
                    class="text-surface-400 shrink-0"
                    title="View receipt"
                    @click.stop
                  >
                    <i class="pi pi-paperclip text-xs" />
                  </a>
                </span>
                <span class="text-sm font-semibold tabular-nums shrink-0" :class="typeColorClass(transaction.type)">
                  <MoneyDisplay :amount="Number(transaction.amount) * amountSign(transaction.type)" sign />
                </span>
              </div>
              <div class="flex items-center justify-between gap-2">
                <div class="flex items-center gap-2 min-w-0">
                  <span class="text-xs text-surface-500 shrink-0">{{ formatDate(transaction.transactionDate) }}</span>
                  <span class="inline-flex items-center gap-1 text-xs font-medium text-surface-600 bg-surface-100 px-2 py-0.5 rounded-lg truncate">
                    <i :class="['pi', categoryIconClass(transaction.categoryIcon)]" />
                    {{ transaction.categoryName }}
                  </span>
                </div>
                <button
                  :data-testid="`mobile-transaction-delete-button-${transaction.id}`"
                  class="w-11 h-11 rounded-md flex items-center justify-center text-surface-500 shrink-0"
                  @click.stop="onDelete(transaction)"
                >
                  <i class="pi pi-trash text-xs" />
                </button>
              </div>
            </div>
          </div>

          <div class="flex items-center justify-between px-5 py-3 border-t border-surface-200">
            <span class="text-sm text-surface-500">Page {{ page + 1 }} of {{ Math.max(totalPages, 1) }}</span>
            <div class="flex items-center gap-1">
              <button
                data-testid="transactions-prev-page-button"
                class="w-7 h-7 rounded-md border border-surface-300 flex items-center justify-center text-surface-600 hover:bg-surface-50 disabled:opacity-40 transition-colors duration-fast ease-out-expo"
                :disabled="page === 0"
                @click="prevPage"
              >
                <i class="pi pi-chevron-left text-xs" />
              </button>
              <button
                data-testid="transactions-next-page-button"
                class="w-7 h-7 rounded-md border border-surface-300 flex items-center justify-center text-surface-600 hover:bg-surface-50 disabled:opacity-40 transition-colors duration-fast ease-out-expo"
                :disabled="page + 1 >= totalPages"
                @click="nextPage"
              >
                <i class="pi pi-chevron-right text-xs" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <Dialog
      v-model:visible="editorOpen"
      modal
      :header="editorTitle"
      :style="{ width: '480px' }"
      :breakpoints="{ '768px': '92vw' }"
      data-testid="transaction-editor-dialog"
    >
      <div class="flex flex-col gap-4">
        <FormError v-if="saveError" :message="saveError" testid="transaction-save-error-banner" />

        <SelectButton
          v-model="editType"
          data-testid="transaction-type-toggle"
          :options="typeToggleOptions"
          option-label="label"
          option-value="value"
          :allow-empty="false"
        />

        <div>
          <label for="transaction-amount-input" class="block text-xs font-semibold text-surface-600 mb-1.5">Amount</label>
          <div class="flex items-baseline gap-2 px-4 py-3 border rounded-lg bg-surface-50 focus-within:ring-2 focus-within:ring-primary-500 focus-within:ring-offset-2 focus-within:ring-offset-surface-100" :class="amountError ? 'border-danger' : 'border-surface-200'">
            <span class="text-sm font-semibold text-surface-500">RM</span>
            <input
              id="transaction-amount-input"
              v-model="editAmount"
              data-testid="transaction-amount-input"
              type="text"
              inputmode="decimal"
              placeholder="0.00"
              class="border-none outline-none bg-transparent text-2xl font-bold text-surface-900 w-full tabular-nums"
            />
          </div>
          <p v-if="amountError" class="text-xs text-danger mt-1.5">{{ amountError }}</p>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div>
            <label for="transaction-date-input" class="block text-xs font-semibold text-surface-600 mb-1.5">Date</label>
            <input
              id="transaction-date-input"
              v-model="editDate"
              data-testid="transaction-date-input"
              type="date"
              class="w-full border rounded-lg px-3 py-2 text-sm text-surface-900 bg-surface-0"
              :class="dateError ? 'border-danger' : 'border-surface-200'"
            />
            <p v-if="dateError" class="text-xs text-danger mt-1.5">{{ dateError }}</p>
          </div>
          <div>
            <label for="transaction-category-select" class="block text-xs font-semibold text-surface-600 mb-1.5">Category</label>
            <Select
              id="transaction-category-select"
              v-model="editCategoryId"
              data-testid="transaction-category-select"
              :options="editorCategoryOptions"
              option-label="name"
              option-value="id"
              placeholder="Select category"
              class="w-full"
              :invalid="!!categoryError"
            />
            <p v-if="categoryError" class="text-xs text-danger mt-1.5">{{ categoryError }}</p>
          </div>
        </div>

        <div>
          <label for="transaction-description-input" class="block text-xs font-semibold text-surface-600 mb-1.5">Description</label>
          <InputText
            id="transaction-description-input"
            v-model="editDescription"
            data-testid="transaction-description-input"
            class="w-full"
            placeholder="e.g. Whole Foods Market"
          />
        </div>

        <div>
          <label class="block text-xs font-semibold text-surface-600 mb-1.5">Receipt</label>

          <FormError v-if="receiptError" :message="receiptError" testid="receipt-error-banner" />

          <div v-if="pendingReceiptFile" class="flex items-center gap-3 p-3 border border-surface-300 rounded-lg bg-surface-50">
            <div class="w-11 h-11 rounded-md bg-surface-200 flex items-center justify-center text-surface-500 shrink-0">
              <i class="pi pi-file text-lg" />
            </div>
            <div class="flex-1 min-w-0">
              <p data-testid="receipt-pending-filename" class="text-sm font-medium text-surface-900 truncate">
                {{ pendingReceiptFile.name }}
              </p>
              <p class="text-xs text-surface-400">Will be attached when you save</p>
            </div>
            <button
              type="button"
              data-testid="receipt-remove-pending-button"
              class="w-7 h-7 rounded-md flex items-center justify-center text-surface-500 hover:bg-surface-200 shrink-0"
              @click="clearPendingReceipt"
            >
              <i class="pi pi-times text-xs" />
            </button>
          </div>

          <template v-else-if="!editReceiptUrl">
            <button
              type="button"
              data-testid="receipt-upload-button"
              class="w-full flex flex-col items-center gap-2 px-6 py-6 border-[1.5px] border-dashed border-surface-300 rounded-lg bg-surface-50 hover:bg-surface-100 hover:border-primary-300 disabled:opacity-60 disabled:cursor-not-allowed transition-colors duration-fast ease-out-expo"
              :disabled="receiptUploading"
              @click="triggerReceiptPicker"
            >
              <i class="pi pi-upload text-surface-400" />
              <span class="text-sm font-medium text-surface-600">
                {{ receiptUploading ? 'Uploading…' : 'Click to upload a receipt' }}
              </span>
              <span class="text-xs text-surface-400">JPG, PNG, WEBP, or PDF — up to 5 MB</span>
            </button>
            <input
              ref="receiptFileInputRef"
              data-testid="receipt-upload-input"
              type="file"
              accept="image/jpeg,image/png,image/webp,application/pdf"
              class="hidden"
              @change="onReceiptSelected"
            />
          </template>

          <div v-else class="flex items-center gap-3 p-3 border border-surface-300 rounded-lg bg-surface-50">
            <div class="w-11 h-11 rounded-md bg-surface-200 flex items-center justify-center text-surface-500 shrink-0">
              <i class="pi pi-file text-lg" />
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium text-surface-900">Receipt attached</p>
              <a
                :href="editReceiptUrl"
                target="_blank"
                rel="noopener"
                data-testid="receipt-view-link"
                class="text-xs text-primary-600 font-medium"
              >
                View
              </a>
            </div>
            <button
              type="button"
              data-testid="receipt-remove-button"
              class="w-7 h-7 rounded-md flex items-center justify-center text-surface-500 hover:bg-surface-200 shrink-0 disabled:opacity-60"
              :disabled="receiptUploading"
              @click="onRemoveReceipt"
            >
              <i class="pi pi-times text-xs" />
            </button>
          </div>
        </div>
      </div>

      <template #footer>
        <Button data-testid="transaction-cancel-button" label="Cancel" severity="secondary" text @click="closeEditor" />
        <Button
          data-testid="transaction-save-button"
          label="Save transaction"
          :loading="saving"
          class="active:scale-[0.98] transition-transform duration-fast ease-out-expo"
          @click="saveEditor"
        />
      </template>
    </Dialog>
  </AppLayout>
</template>
