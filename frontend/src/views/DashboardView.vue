<script setup lang="ts">
import { computed, onMounted, ref, type ComponentPublicInstance } from 'vue'
import Button from 'primevue/button'
import Chart from 'primevue/chart'
import AppLayout from '../layouts/AppLayout.vue'
import MoneyDisplay from '../components/common/MoneyDisplay.vue'
import { fetchDashboard } from '../api/dashboard'
import { categoryIconClass } from '../lib/categoryIcons'
import type { CategoryBudgetLine } from '../types/budget'
import type { DashboardResponse } from '../types/dashboard'

type LoadState = 'loading' | 'error' | 'ready'

const MONTHS = 6
const RECENT_LIMIT = 5

const loadState = ref<LoadState>('loading')
const dashboard = ref<DashboardResponse | null>(null)

// Chart.js draws to a <canvas>, so it needs literal color strings — these
// hidden elements exist only so we can read the *resolved* value of design
// tokens (Tailwind utility classes) rather than hardcoding hex here, same
// technique as AdminDashboardView.
const DONUT_PALETTE_CLASSES = [
  'text-primary-600',
  'text-sky-600',
  'text-amber-600',
  'text-violet-600',
  'text-emerald-600',
  'text-rose-600',
  'text-cyan-600',
  'text-primary-300',
]
const paletteRefs = ref<(HTMLElement | null)[]>([])
const paletteColors = ref<string[]>([])
const greenRef = ref<HTMLElement | null>(null)
const redRef = ref<HTMLElement | null>(null)
const greenColor = ref('')
const redColor = ref('')

function setPaletteRef(el: Element | ComponentPublicInstance | null, index: number) {
  paletteRefs.value[index] = el as HTMLElement | null
}

onMounted(() => {
  paletteColors.value = paletteRefs.value.map((el) => (el ? getComputedStyle(el).color : '#94A3B8'))
  if (greenRef.value) greenColor.value = getComputedStyle(greenRef.value).color
  if (redRef.value) redColor.value = getComputedStyle(redRef.value).color
  loadDashboard()
})

async function loadDashboard() {
  loadState.value = 'loading'
  try {
    dashboard.value = await fetchDashboard(MONTHS, RECENT_LIMIT)
    loadState.value = 'ready'
  } catch {
    loadState.value = 'error'
  }
}

const isEmpty = computed(() => loadState.value === 'ready' && (dashboard.value?.recentTransactions.length ?? 0) === 0)

const periodLabel = computed(() => {
  const month = dashboard.value?.budgetUtilisation.periodMonth
  if (!month) return ''
  return new Date(month + 'T00:00:00').toLocaleDateString('en-MY', { month: 'long', year: 'numeric' })
})

// --- summary cards ---

function toNumber(value: number | string): number {
  return typeof value === 'number' ? value : Number(value)
}

const summaryCards = computed(() => {
  const summary = dashboard.value?.summary
  if (!summary) return []
  const balance = toNumber(summary.thisMonthBalance)
  return [
    { testid: 'summary-income', label: 'Income', icon: 'pi pi-arrow-up', amount: summary.thisMonthIncome, colorClass: 'text-green-700' },
    { testid: 'summary-expense', label: 'Expense', icon: 'pi pi-arrow-down', amount: summary.thisMonthExpense, colorClass: 'text-red-600' },
    {
      testid: 'summary-balance',
      label: 'Balance',
      icon: 'pi pi-wallet',
      amount: summary.thisMonthBalance,
      colorClass: balance < 0 ? 'text-red-600' : 'text-surface-900',
    },
  ]
})

// --- budget utilisation (same thresholds/colors as BudgetsView) ---

function statusColorClass(pct: number | null): string {
  if (pct === null) return 'text-surface-400'
  if (pct >= 100) return 'text-red-600'
  if (pct >= 80) return 'text-amber-600'
  return 'text-surface-700'
}
function statusBarClass(pct: number | null): string {
  if (pct === null) return 'bg-surface-300'
  if (pct >= 100) return 'bg-red-600'
  if (pct >= 80) return 'bg-amber-600'
  return 'bg-surface-700'
}
function clampedWidth(pct: number | string | null): number {
  if (pct === null) return 0
  return Math.min(100, Number(pct))
}
function numberOrNull(value: number | string | null): number | null {
  return value === null ? null : Number(value)
}
function budgetedCategories(categories: CategoryBudgetLine[] | undefined): CategoryBudgetLine[] {
  return (categories ?? []).filter((c) => c.budgetId !== null)
}

// --- spending by category (donut) ---

const donutTotal = computed(() =>
  (dashboard.value?.expenseByCategory ?? []).reduce((sum, c) => sum + toNumber(c.amount), 0),
)

const donutChartData = computed(() => {
  const categories = dashboard.value?.expenseByCategory ?? []
  if (!categories.length) return null
  return {
    labels: categories.map((c) => c.categoryName),
    datasets: [
      {
        data: categories.map((c) => toNumber(c.amount)),
        backgroundColor: categories.map((_, i) => paletteColors.value[i % paletteColors.value.length]),
        borderWidth: 0,
      },
    ],
  }
})

const donutOptions = {
  plugins: { legend: { display: false } },
  cutout: '68%',
  responsive: true,
  maintainAspectRatio: false,
}

const categoryLegend = computed(() => {
  const categories = dashboard.value?.expenseByCategory ?? []
  const total = donutTotal.value
  return categories.map((c, i) => ({
    categoryId: c.categoryId,
    name: c.categoryName,
    color: paletteColors.value[i % paletteColors.value.length],
    pctDisplay: total > 0 ? `${Math.round((toNumber(c.amount) / total) * 100)}%` : '0%',
  }))
})

// --- monthly trend (net) & income vs expense ---

function monthLabel(month: string): string {
  return new Date(`${month}T00:00:00`).toLocaleDateString('en-GB', { month: 'short' })
}

const netTrendChartData = computed(() => {
  const points = dashboard.value?.monthlyTrend ?? []
  const nets = points.map((p) => toNumber(p.income) - toNumber(p.expense))
  return {
    labels: points.map((p) => monthLabel(p.month)),
    datasets: [
      {
        data: nets,
        backgroundColor: nets.map((n) => (n >= 0 ? greenColor.value : redColor.value)),
        borderRadius: 3,
      },
    ],
  }
})

const netTrendOptions = {
  plugins: { legend: { display: false } },
  scales: { x: { grid: { display: false } }, y: { display: false } },
  responsive: true,
  maintainAspectRatio: false,
}

const comparisonChartData = computed(() => {
  const points = dashboard.value?.monthlyTrend ?? []
  return {
    labels: points.map((p) => monthLabel(p.month)),
    datasets: [
      { label: 'Income', data: points.map((p) => toNumber(p.income)), backgroundColor: greenColor.value, borderRadius: 3 },
      { label: 'Expense', data: points.map((p) => toNumber(p.expense)), backgroundColor: redColor.value, borderRadius: 3 },
    ],
  }
})

const comparisonOptions = {
  plugins: { legend: { display: false } },
  scales: { x: { grid: { display: false } }, y: { display: false } },
  responsive: true,
  maintainAspectRatio: false,
}

// --- recent transactions ---

function dateDisplay(iso: string): string {
  return new Date(iso + 'T00:00:00').toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })
}

function signedAmount(tx: { type: string; amount: number | string }): number {
  return toNumber(tx.amount) * (tx.type === 'INCOME' ? 1 : -1)
}
</script>

<template>
  <AppLayout title="Dashboard">
    <span
      v-for="(cls, i) in DONUT_PALETTE_CLASSES"
      :key="cls"
      :ref="(el) => setPaletteRef(el, i)"
      :class="cls"
      class="hidden"
      aria-hidden="true"
    ></span>
    <span ref="greenRef" class="text-green-700 hidden" aria-hidden="true"></span>
    <span ref="redRef" class="text-red-600 hidden" aria-hidden="true"></span>

    <div class="flex flex-col gap-6">
      <div>
        <h1 class="text-2xl font-bold text-surface-900">Dashboard</h1>
        <p data-testid="dashboard-period-label" class="text-sm text-surface-500 mt-1">{{ periodLabel }}</p>
      </div>

      <!-- error state -->
      <div
        v-if="loadState === 'error'"
        data-testid="dashboard-error-state"
        class="flex flex-col items-center justify-center gap-4 py-20 px-8 bg-white border border-surface-200 rounded-lg"
      >
        <div class="w-14 h-14 rounded-lg bg-red-50 flex items-center justify-center">
          <i class="pi pi-exclamation-triangle text-red-600 text-2xl" />
        </div>
        <p class="text-base font-semibold text-surface-900">Couldn't load your dashboard</p>
        <p class="text-sm text-surface-500 text-center max-w-sm">
          Something went wrong while fetching your data. Check your connection and try again.
        </p>
        <Button data-testid="dashboard-retry-button" label="Retry" icon="pi pi-refresh" @click="loadDashboard" />
      </div>

      <!-- loading state -->
      <div v-else-if="loadState === 'loading'" data-testid="dashboard-loading-skeleton" class="grid grid-cols-12 gap-6">
        <div v-for="n in 3" :key="n" class="col-span-4 bg-white border border-surface-200 rounded-lg p-5 flex flex-col gap-2">
          <div class="w-24 h-3 rounded bg-surface-200 animate-pulse" />
          <div class="w-28 h-7 rounded bg-surface-200 animate-pulse" />
        </div>
        <div class="col-span-12 bg-white border border-surface-200 rounded-lg p-6">
          <div class="w-full h-24 rounded bg-surface-200 animate-pulse" />
        </div>
        <div class="col-span-5 bg-white border border-surface-200 rounded-lg p-6">
          <div class="w-40 h-40 rounded-full bg-surface-200 animate-pulse mx-auto" />
        </div>
        <div class="col-span-7 bg-white border border-surface-200 rounded-lg p-6">
          <div class="w-full h-48 rounded bg-surface-200 animate-pulse" />
        </div>
      </div>

      <!-- empty state -->
      <div
        v-else-if="isEmpty"
        data-testid="dashboard-empty-state"
        class="flex flex-col items-center justify-center gap-4 py-20 px-8 bg-white border border-surface-200 rounded-lg"
      >
        <div class="w-14 h-14 rounded-lg bg-surface-100 flex items-center justify-center">
          <i class="pi pi-chart-bar text-surface-400 text-2xl" />
        </div>
        <p class="text-base font-semibold text-surface-900">Welcome to ExpenseWise</p>
        <p class="text-sm text-surface-500 text-center max-w-sm">
          Your dashboard will fill in with income, spending, and budget insights as soon as you log a transaction.
        </p>
        <router-link
          data-testid="dashboard-add-first-transaction-link"
          to="/transactions"
          class="flex items-center gap-2 bg-primary-600 text-white text-sm font-semibold px-4 py-2.5 rounded-lg mt-2"
        >
          <i class="pi pi-plus" />
          Add your first transaction
        </router-link>
      </div>

      <!-- ready state -->
      <div v-else data-testid="dashboard-content" class="grid grid-cols-12 gap-6">
        <!-- summary cards -->
        <div
          v-for="card in summaryCards"
          :key="card.testid"
          :data-testid="card.testid"
          class="col-span-4 bg-white border border-surface-200 rounded-lg p-5 flex flex-col gap-2"
        >
          <div class="flex items-center gap-2">
            <div class="w-7 h-7 rounded-lg bg-surface-100 flex items-center justify-center text-surface-600">
              <i :class="card.icon" class="text-xs" />
            </div>
            <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide">{{ card.label }}</span>
          </div>
          <span class="text-2xl font-bold tabular-nums" :class="card.colorClass">
            <MoneyDisplay :amount="card.amount" />
          </span>
        </div>

        <!-- budget utilisation -->
        <div class="col-span-12 bg-white border border-surface-200 rounded-lg p-6">
          <div class="flex items-center justify-between mb-4">
            <span class="text-sm font-semibold text-surface-900">Budget Utilisation</span>
            <span
              v-if="dashboard!.budgetUtilisation.overall.amount !== null"
              class="text-sm font-semibold"
              :class="statusColorClass(numberOrNull(dashboard!.budgetUtilisation.overall.progressPercent))"
            >
              <MoneyDisplay :amount="dashboard!.budgetUtilisation.overall.spent" /> of
              <MoneyDisplay :amount="dashboard!.budgetUtilisation.overall.amount" />
            </span>
            <span v-else class="text-sm text-surface-400">No overall budget set</span>
          </div>
          <div class="w-full h-2.5 rounded-full bg-surface-200 overflow-hidden mb-6">
            <div
              class="h-full rounded-full"
              :class="statusBarClass(numberOrNull(dashboard!.budgetUtilisation.overall.progressPercent))"
              :style="{ width: clampedWidth(dashboard!.budgetUtilisation.overall.progressPercent) + '%' }"
            />
          </div>

          <div
            v-if="budgetedCategories(dashboard!.budgetUtilisation.categories).length"
            data-testid="dashboard-budget-categories"
            class="grid grid-cols-3 gap-x-6 gap-y-5"
          >
            <div v-for="b in budgetedCategories(dashboard!.budgetUtilisation.categories)" :key="b.categoryId">
              <div class="flex items-center justify-between mb-1.5">
                <span class="text-sm font-medium text-surface-700 flex items-center gap-1.5">
                  <i :class="['pi', categoryIconClass(b.categoryIcon)]" class="text-surface-400 text-xs" />
                  {{ b.categoryName }}
                </span>
                <span class="text-xs text-surface-500 tabular-nums">
                  <MoneyDisplay :amount="b.spent" /> / <MoneyDisplay :amount="b.amount!" />
                </span>
              </div>
              <div class="w-full h-1.5 rounded-full bg-surface-200 overflow-hidden">
                <div
                  class="h-full rounded-full"
                  :class="statusBarClass(numberOrNull(b.progressPercent))"
                  :style="{ width: clampedWidth(b.progressPercent) + '%' }"
                />
              </div>
            </div>
          </div>
          <p v-else class="text-sm text-surface-400">No category budgets set yet.</p>
        </div>

        <!-- spending by category (donut) -->
        <div class="col-span-5 bg-white border border-surface-200 rounded-lg p-6">
          <span class="text-sm font-semibold text-surface-900 block mb-4">Spending by Category</span>
          <div v-if="donutChartData" class="flex items-center gap-6">
            <div data-testid="dashboard-category-donut" class="relative w-40 h-40 shrink-0">
              <Chart type="doughnut" :data="donutChartData" :options="donutOptions" class="w-full h-full" />
              <div class="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                <span class="text-base font-bold text-surface-900 tabular-nums">
                  <MoneyDisplay :amount="donutTotal" />
                </span>
                <span class="text-xs text-surface-400">this month</span>
              </div>
            </div>
            <div class="flex-1 flex flex-col gap-2.5 min-w-0">
              <div v-for="c in categoryLegend" :key="c.categoryId" class="flex items-center gap-2">
                <div class="w-2 h-2 rounded-sm shrink-0" :style="{ backgroundColor: c.color }" />
                <span class="text-sm text-surface-700 flex-1 truncate">{{ c.name }}</span>
                <span class="text-xs text-surface-500 tabular-nums">{{ c.pctDisplay }}</span>
              </div>
            </div>
          </div>
          <p v-else data-testid="dashboard-category-donut-empty" class="text-sm text-surface-400 text-center py-10">
            No expenses recorded this month yet.
          </p>
        </div>

        <!-- monthly trend (net) -->
        <div class="col-span-7 bg-white border border-surface-200 rounded-lg p-6">
          <span class="text-sm font-semibold text-surface-900 block mb-4">Monthly Trend — Net (last {{ MONTHS }} months)</span>
          <div data-testid="dashboard-net-trend-chart" class="h-52">
            <Chart type="bar" :data="netTrendChartData" :options="netTrendOptions" class="h-full" />
          </div>
        </div>

        <!-- income vs expense -->
        <div class="col-span-12 bg-white border border-surface-200 rounded-lg p-6">
          <div class="flex items-center justify-between mb-4">
            <span class="text-sm font-semibold text-surface-900">Income vs Expense (last {{ MONTHS }} months)</span>
            <div class="flex items-center gap-4">
              <span class="flex items-center gap-1.5 text-xs text-surface-500">
                <span class="w-2 h-2 rounded-sm bg-green-700" />Income
              </span>
              <span class="flex items-center gap-1.5 text-xs text-surface-500">
                <span class="w-2 h-2 rounded-sm bg-red-600" />Expense
              </span>
            </div>
          </div>
          <div data-testid="dashboard-income-expense-chart" class="h-44">
            <Chart type="bar" :data="comparisonChartData" :options="comparisonOptions" class="h-full" />
          </div>
        </div>

        <!-- recent transactions -->
        <div class="col-span-12 bg-white border border-surface-200 rounded-lg overflow-hidden">
          <div class="flex items-center justify-between px-6 py-4 border-b border-surface-200">
            <span class="text-sm font-semibold text-surface-900">Recent Transactions</span>
            <router-link data-testid="dashboard-view-all-transactions-link" to="/transactions" class="text-sm font-semibold text-primary-600">
              View all
            </router-link>
          </div>
          <div data-testid="dashboard-recent-transactions-list">
            <div
              v-for="tx in dashboard!.recentTransactions"
              :key="tx.id"
              :data-testid="`dashboard-recent-transaction-${tx.id}`"
              class="flex items-center gap-4 px-6 py-3.5 border-b border-surface-100 last:border-b-0"
            >
              <span class="w-16 text-sm text-surface-500 shrink-0">{{ dateDisplay(tx.transactionDate) }}</span>
              <span class="flex-1 text-sm font-medium text-surface-900 truncate">{{ tx.description || tx.categoryName }}</span>
              <span class="text-xs font-medium text-surface-600 bg-surface-100 px-2.5 py-1 rounded-lg shrink-0">{{ tx.categoryName }}</span>
              <span
                class="w-28 text-right text-sm font-semibold tabular-nums shrink-0"
                :class="tx.type === 'INCOME' ? 'text-green-700' : 'text-red-600'"
              >
                <MoneyDisplay :amount="signedAmount(tx)" sign />
              </span>
            </div>
            <div v-if="!dashboard!.recentTransactions.length" class="px-6 py-8 text-sm text-surface-500 text-center">
              No transactions yet.
            </div>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>
