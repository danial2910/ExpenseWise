<script setup lang="ts">
import { computed, onMounted, ref, type ComponentPublicInstance } from 'vue'
import Chart from 'primevue/chart'
import AppLayout from '../layouts/AppLayout.vue'
import MoneyDisplay from '../components/common/MoneyDisplay.vue'
import EmptyState from '../components/common/EmptyState.vue'
import ErrorState from '../components/common/ErrorState.vue'
import ChartTooltip from '../components/common/ChartTooltip.vue'
import { useExternalTooltip } from '../lib/chartTooltip'
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
// technique as AdminDashboardView. The donut stays inside the cyan → indigo
// family the brief asked for (no "purple" token exists yet, so aurora-indigo
// stands in — closest hue this app has to Nexora's violet ring stop).
const DONUT_PALETTE_CLASSES = [
  'text-primary-300',
  'text-primary-500',
  'text-primary-700',
  'text-aurora-teal',
  'text-aurora-blue',
  'text-aurora-indigo',
  'text-primary-400',
  'text-primary-600',
]
const paletteRefs = ref<(HTMLElement | null)[]>([])
const paletteColors = ref<string[]>([])
const successRef = ref<HTMLElement | null>(null)
const dangerRef = ref<HTMLElement | null>(null)
const primaryRef = ref<HTMLElement | null>(null)
const successColor = ref('')
const dangerColor = ref('')
const primaryColor = ref('')

function setPaletteRef(el: Element | ComponentPublicInstance | null, index: number) {
  paletteRefs.value[index] = el as HTMLElement | null
}

const { tooltipState: trendTooltip, externalTooltipHandler: trendTooltipHandler } = useExternalTooltip()
const { tooltipState: donutTooltip, externalTooltipHandler: donutTooltipHandler } = useExternalTooltip()

onMounted(() => {
  paletteColors.value = paletteRefs.value.map((el) => (el ? getComputedStyle(el).color : '#5B6472'))
  if (successRef.value) successColor.value = getComputedStyle(successRef.value).color
  if (dangerRef.value) dangerColor.value = getComputedStyle(dangerRef.value).color
  if (primaryRef.value) primaryColor.value = getComputedStyle(primaryRef.value).color
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

function toNumber(value: number | string): number {
  return typeof value === 'number' ? value : Number(value)
}

// --- KPI row ---
// Nexora's reference shows a green delta ("+12% vs last month") on every
// card. The dashboard API has no prior-period figures to compare against —
// only this month's — so no delta is computed or shown anywhere on this
// page. See the phase summary for the full list of omissions like this one.

const sparklineOptions = {
  plugins: { legend: { display: false }, tooltip: { enabled: false } },
  scales: { x: { display: false }, y: { display: false } },
  elements: { point: { radius: 0 }, line: { borderWidth: 2, tension: 0.35 } },
  responsive: true,
  maintainAspectRatio: false,
}

function sparklineData(points: number[], color: string) {
  return {
    labels: points.map((_, i) => i),
    datasets: [{ data: points, borderColor: color, fill: false }],
  }
}

const incomeSeries = computed(() => (dashboard.value?.monthlyTrend ?? []).map((p) => toNumber(p.income)))
const expenseSeries = computed(() => (dashboard.value?.monthlyTrend ?? []).map((p) => toNumber(p.expense)))

const budgetRemainingPercent = computed(() => {
  const pct = dashboard.value?.budgetUtilisation.overall.progressPercent
  return pct === null || pct === undefined ? null : Math.min(100, Math.max(0, Number(pct)))
})

// SVG ring geometry — a fixed-radius circle, dash offset driven directly by
// the server-computed progressPercent (no client-side derivation beyond the
// circumference arithmetic every SVG progress ring needs).
const RING_RADIUS = 15
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS
const ringOffset = computed(() => {
  const pct = budgetRemainingPercent.value ?? 0
  return RING_CIRCUMFERENCE * (1 - pct / 100)
})

const kpiCards = computed(() => {
  const summary = dashboard.value?.summary
  if (!summary) return []
  const remaining = dashboard.value!.budgetUtilisation.overall.remaining
  return [
    {
      testid: 'summary-balance',
      label: 'Total Balance',
      icon: 'pi-wallet',
      amount: summary.overallBalance,
      tone: toNumber(summary.overallBalance) < 0 ? 'text-danger' : 'text-surface-900',
    },
    {
      testid: 'summary-income',
      label: 'Income',
      icon: 'pi-arrow-up',
      amount: summary.thisMonthIncome,
      tone: 'text-success',
      sparkline: incomeSeries.value.length > 1 ? sparklineData(incomeSeries.value, successColor.value) : null,
    },
    {
      testid: 'summary-expense',
      label: 'Expense',
      icon: 'pi-arrow-down',
      amount: summary.thisMonthExpense,
      tone: 'text-danger',
      sparkline: expenseSeries.value.length > 1 ? sparklineData(expenseSeries.value, dangerColor.value) : null,
    },
    {
      testid: 'summary-budget-remaining',
      label: 'Budget Remaining',
      icon: 'pi-shield',
      amount: remaining,
      tone: remaining !== null && toNumber(remaining) < 0 ? 'text-danger' : 'text-surface-900',
      ring: true,
    },
  ]
})

// --- budget utilisation (feed-style panel, same thresholds/colors as BudgetsView) ---

function statusColorClass(pct: number | null): string {
  if (pct === null) return 'text-surface-400'
  if (pct >= 100) return 'text-danger'
  if (pct >= 80) return 'text-warning'
  return 'text-surface-700'
}
function statusDotClass(pct: number | null): string {
  if (pct === null) return 'bg-surface-300'
  if (pct >= 100) return 'bg-danger'
  if (pct >= 80) return 'bg-warning'
  return 'bg-success'
}
function statusBarClass(pct: number | null): string {
  if (pct === null) return 'bg-surface-300'
  if (pct >= 100) return 'bg-danger'
  if (pct >= 80) return 'bg-warning'
  return 'bg-success'
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
  plugins: { legend: { display: false }, tooltip: { enabled: false, external: donutTooltipHandler } },
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
    amount: c.amount,
    color: paletteColors.value[i % paletteColors.value.length],
    pctDisplay: total > 0 ? `${Math.round((toNumber(c.amount) / total) * 100)}%` : '0%',
  }))
})

// --- income vs expense (single line/area chart — replaces the old separate
// "net trend" bar chart, since net is fully implied by income+expense shown
// together; see phase summary) ---

function monthLabel(month: string): string {
  return new Date(`${month}T00:00:00`).toLocaleDateString('en-GB', { month: 'short' })
}

const trendChartData = computed(() => {
  const points = dashboard.value?.monthlyTrend ?? []
  return {
    labels: points.map((p) => monthLabel(p.month)),
    datasets: [
      {
        label: 'Income',
        data: points.map((p) => toNumber(p.income)),
        borderColor: successColor.value,
        backgroundColor: successColor.value.replace('rgb', 'rgba').replace(')', ', 0.12)'),
        fill: true,
        tension: 0.35,
        pointRadius: 0,
        pointHoverRadius: 4,
        borderWidth: 2,
      },
      {
        label: 'Expense',
        data: points.map((p) => toNumber(p.expense)),
        borderColor: dangerColor.value,
        backgroundColor: dangerColor.value.replace('rgb', 'rgba').replace(')', ', 0.12)'),
        fill: true,
        tension: 0.35,
        pointRadius: 0,
        pointHoverRadius: 4,
        borderWidth: 2,
      },
    ],
  }
})

const trendChartOptions = {
  plugins: { legend: { display: false }, tooltip: { enabled: false, external: trendTooltipHandler } },
  interaction: { mode: 'index', intersect: false },
  scales: {
    x: { grid: { display: false }, ticks: { color: paletteColors.value[0] || '#5B6472' } },
    y: { display: false },
  },
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
  <AppLayout title="Dashboard" fab-label="Add transaction" fab-to="/transactions?add=1">
    <span
      v-for="(cls, i) in DONUT_PALETTE_CLASSES"
      :key="cls"
      :ref="(el) => setPaletteRef(el, i)"
      :class="cls"
      class="hidden"
      aria-hidden="true"
    ></span>
    <span ref="successRef" class="text-success hidden" aria-hidden="true"></span>
    <span ref="dangerRef" class="text-danger hidden" aria-hidden="true"></span>
    <span ref="primaryRef" class="text-primary-400 hidden" aria-hidden="true"></span>

    <ChartTooltip :state="trendTooltip" />
    <ChartTooltip :state="donutTooltip" />

    <div class="flex flex-col gap-6">
      <div>
        <h1 class="font-display text-2xl font-semibold tracking-tight text-surface-900">Dashboard</h1>
        <p data-testid="dashboard-period-label" class="text-sm text-surface-500 mt-1">{{ periodLabel }}</p>
      </div>

      <ErrorState
        v-if="loadState === 'error'"
        testid="dashboard-error-state"
        retry-testid="dashboard-retry-button"
        title="Couldn't load your dashboard"
        description="Something went wrong while fetching your data. Check your connection and try again."
        class="bg-surface-0 border border-surface-200 rounded-xl"
        @retry="loadDashboard"
      />

      <!-- loading state -->
      <div v-else-if="loadState === 'loading'" data-testid="dashboard-loading-skeleton" class="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div v-for="n in 4" :key="n" class="lg:col-span-3 bg-surface-0 border border-surface-200 rounded-xl p-5 flex flex-col gap-2">
          <div class="w-20 h-3 rounded bg-surface-200 animate-pulse" />
          <div class="w-24 h-7 rounded bg-surface-200 animate-pulse" />
        </div>
        <div class="lg:col-span-7 bg-surface-0 border border-surface-200 rounded-xl p-6">
          <div class="w-full h-48 rounded bg-surface-200 animate-pulse" />
        </div>
        <div class="lg:col-span-5 bg-surface-0 border border-surface-200 rounded-xl p-6">
          <div class="w-40 h-40 rounded-full bg-surface-200 animate-pulse mx-auto" />
        </div>
        <div class="lg:col-span-7 bg-surface-0 border border-surface-200 rounded-xl p-6">
          <div class="w-full h-48 rounded bg-surface-200 animate-pulse" />
        </div>
        <div class="lg:col-span-5 bg-surface-0 border border-surface-200 rounded-xl p-6">
          <div class="w-full h-48 rounded bg-surface-200 animate-pulse" />
        </div>
      </div>

      <EmptyState
        v-else-if="isEmpty"
        testid="dashboard-empty-state"
        icon="pi-chart-bar"
        title="Welcome to ExpenseWise"
        description="Your dashboard will fill in with income, spending, and budget insights as soon as you log a transaction."
        class="bg-surface-0 border border-surface-200 rounded-xl"
      >
        <template #action>
          <router-link
            data-testid="dashboard-add-first-transaction-link"
            to="/transactions"
            class="flex items-center gap-2 bg-primary-500 text-surface-100 text-sm font-semibold px-4 py-2.5 rounded-lg active:scale-[0.98] transition-transform duration-fast ease-out-expo"
          >
            <i class="pi pi-plus" />
            Add your first transaction
          </router-link>
        </template>
      </EmptyState>

      <!-- ready state -->
      <div v-else data-testid="dashboard-content" class="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <!-- KPI row -->
        <div
          v-for="card in kpiCards"
          :key="card.testid"
          :data-testid="card.testid"
          class="lg:col-span-3 sm:col-span-1 bg-surface-0 border border-surface-200 rounded-xl p-5 flex flex-col gap-3"
        >
          <div class="flex items-center gap-2">
            <div class="w-7 h-7 rounded-lg bg-surface-50 flex items-center justify-center text-surface-600 shrink-0">
              <i :class="['pi', card.icon]" class="text-xs" />
            </div>
            <span class="text-[11px] font-semibold text-surface-500 uppercase tracking-wide truncate">{{ card.label }}</span>
          </div>

          <div class="flex items-end justify-between gap-3">
            <span v-if="card.amount !== null" class="text-2xl font-bold tabular-nums font-display" :class="card.tone">
              <MoneyDisplay :amount="card.amount" />
            </span>
            <span v-else class="text-sm text-surface-400 font-medium">No budget set</span>

            <div v-if="'sparkline' in card && card.sparkline" class="w-16 h-8 shrink-0">
              <Chart type="line" :data="card.sparkline" :options="sparklineOptions" class="w-full h-full" />
            </div>
            <svg v-else-if="'ring' in card && card.ring && budgetRemainingPercent !== null" width="36" height="36" viewBox="0 0 36 36" class="shrink-0 -rotate-90">
              <circle cx="18" cy="18" :r="RING_RADIUS" fill="none" stroke-width="4" class="stroke-surface-200" />
              <circle
                cx="18"
                cy="18"
                :r="RING_RADIUS"
                fill="none"
                stroke-width="4"
                stroke-linecap="round"
                class="stroke-primary-500"
                :stroke-dasharray="RING_CIRCUMFERENCE"
                :stroke-dashoffset="ringOffset"
              />
            </svg>
          </div>
        </div>

        <!-- Income vs Expense (line/area) -->
        <div class="lg:col-span-7 bg-surface-0 border border-surface-200 rounded-xl p-6">
          <div class="flex items-center justify-between mb-1">
            <span class="text-sm font-semibold text-surface-900">Income vs Expense</span>
            <div class="flex items-center gap-4">
              <span class="flex items-center gap-1.5 text-xs text-surface-500">
                <span class="w-2 h-2 rounded-full bg-success" />Income
              </span>
              <span class="flex items-center gap-1.5 text-xs text-surface-500">
                <span class="w-2 h-2 rounded-full bg-danger" />Expense
              </span>
            </div>
          </div>
          <p class="text-xs text-surface-400 mb-4">Last {{ MONTHS }} months</p>
          <div data-testid="dashboard-income-expense-chart" class="h-52 lg:h-60">
            <Chart type="line" :data="trendChartData" :options="trendChartOptions" class="h-full" />
          </div>
        </div>

        <!-- Spending by Category (donut) -->
        <div class="lg:col-span-5 bg-surface-0 border border-surface-200 rounded-xl p-6">
          <span class="text-sm font-semibold text-surface-900 block mb-4">Spending by Category</span>
          <div v-if="donutChartData" class="flex flex-col items-center gap-6">
            <div data-testid="dashboard-category-donut" class="relative w-40 h-40 shrink-0">
              <Chart type="doughnut" :data="donutChartData" :options="donutOptions" class="w-full h-full" />
              <div class="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                <span class="text-base font-bold text-surface-900 tabular-nums font-display">
                  <MoneyDisplay :amount="donutTotal" />
                </span>
                <span class="text-xs text-surface-400">this month</span>
              </div>
            </div>
            <div class="w-full flex flex-col gap-2.5 min-w-0">
              <div v-for="c in categoryLegend" :key="c.categoryId" class="flex items-center gap-2">
                <span class="w-2 h-2 rounded-full shrink-0" :style="{ backgroundColor: c.color }" />
                <span class="text-sm text-surface-700 flex-1 truncate">{{ c.name }}</span>
                <span class="text-xs text-surface-500 tabular-nums"><MoneyDisplay :amount="c.amount" /></span>
                <span class="text-xs text-success font-semibold tabular-nums w-10 text-right">{{ c.pctDisplay }}</span>
              </div>
            </div>
          </div>
          <p v-else data-testid="dashboard-category-donut-empty" class="text-sm text-surface-400 text-center py-10">
            No expenses recorded this month yet.
          </p>
        </div>

        <!-- Recent Transactions (table) -->
        <div class="lg:col-span-7 bg-surface-0 border border-surface-200 rounded-xl overflow-hidden">
          <div class="flex items-center justify-between px-6 py-4 border-b border-surface-200 gap-3">
            <span class="text-sm font-semibold text-surface-900">Recent Transactions</span>
            <div class="flex items-center gap-4 shrink-0">
              <router-link
                data-testid="dashboard-quick-add-link"
                to="/transactions?add=1"
                class="hidden sm:flex items-center gap-1.5 text-xs font-semibold text-surface-600 border border-surface-300 rounded-lg px-2.5 py-1.5 hover:border-primary-300 hover:text-primary-300 transition-colors duration-fast ease-out-expo"
              >
                <i class="pi pi-plus text-[10px]" />
                New
              </router-link>
              <router-link data-testid="dashboard-view-all-transactions-link" to="/transactions" class="text-sm font-semibold text-primary-300 hover:text-primary-200 transition-colors duration-fast ease-out-expo">
                View all
              </router-link>
            </div>
          </div>
          <div class="hidden sm:grid grid-cols-[1fr_110px_110px_90px] px-6 py-2.5 bg-surface-50">
            <span class="text-[11px] font-semibold text-surface-500 uppercase tracking-wide">Description</span>
            <span class="text-[11px] font-semibold text-surface-500 uppercase tracking-wide">Type</span>
            <span class="text-[11px] font-semibold text-surface-500 uppercase tracking-wide text-right">Amount</span>
            <span class="text-[11px] font-semibold text-surface-500 uppercase tracking-wide text-right">Date</span>
          </div>
          <div data-testid="dashboard-recent-transactions-list">
            <div
              v-for="tx in dashboard!.recentTransactions"
              :key="tx.id"
              :data-testid="`dashboard-recent-transaction-${tx.id}`"
              class="px-4 sm:px-6 py-3.5 border-b border-surface-100 last:border-b-0"
            >
              <!-- desktop/tablet row -->
              <div class="hidden sm:grid grid-cols-[1fr_110px_110px_90px] items-center">
                <span class="text-sm font-medium text-surface-900 truncate pr-3">{{ tx.description || tx.categoryName }}</span>
                <span>
                  <span
                    class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold"
                    :class="tx.type === 'INCOME' ? 'bg-success-bg text-success' : 'bg-danger-bg text-danger'"
                  >
                    {{ tx.type === 'INCOME' ? 'Income' : 'Expense' }}
                  </span>
                </span>
                <span
                  class="text-right text-sm font-semibold tabular-nums"
                  :class="tx.type === 'INCOME' ? 'text-success' : 'text-danger'"
                >
                  <MoneyDisplay :amount="signedAmount(tx)" sign />
                </span>
                <span class="text-right text-sm text-surface-500">{{ dateDisplay(tx.transactionDate) }}</span>
              </div>
              <!-- mobile card -->
              <div class="sm:hidden flex flex-col gap-1.5">
                <div class="flex items-center justify-between gap-3">
                  <span class="text-sm font-medium text-surface-900 truncate">{{ tx.description || tx.categoryName }}</span>
                  <span
                    class="text-sm font-semibold tabular-nums shrink-0"
                    :class="tx.type === 'INCOME' ? 'text-success' : 'text-danger'"
                  >
                    <MoneyDisplay :amount="signedAmount(tx)" sign />
                  </span>
                </div>
                <div class="flex items-center gap-2">
                  <span class="text-xs text-surface-500">{{ dateDisplay(tx.transactionDate) }}</span>
                  <span
                    class="inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold"
                    :class="tx.type === 'INCOME' ? 'bg-success-bg text-success' : 'bg-danger-bg text-danger'"
                  >
                    {{ tx.type === 'INCOME' ? 'Income' : 'Expense' }}
                  </span>
                </div>
              </div>
            </div>
            <div v-if="!dashboard!.recentTransactions.length" class="px-6 py-8 text-sm text-surface-500 text-center">
              No transactions yet.
            </div>
          </div>
        </div>

        <!-- Budget Utilisation (activity-feed panel) -->
        <div class="lg:col-span-5 bg-surface-0 border border-surface-200 rounded-xl p-6 flex flex-col">
          <div class="flex items-center justify-between mb-3">
            <span class="text-sm font-semibold text-surface-900">Budget Utilisation</span>
            <span
              v-if="dashboard!.budgetUtilisation.overall.amount !== null"
              class="text-xs font-semibold"
              :class="statusColorClass(numberOrNull(dashboard!.budgetUtilisation.overall.progressPercent))"
            >
              <MoneyDisplay :amount="dashboard!.budgetUtilisation.overall.spent" /> / <MoneyDisplay :amount="dashboard!.budgetUtilisation.overall.amount" />
            </span>
            <span v-else class="text-xs text-surface-400">No overall budget set</span>
          </div>
          <div class="w-full h-2 rounded-full bg-surface-200 overflow-hidden mb-5">
            <div
              class="h-full rounded-full"
              :class="statusBarClass(numberOrNull(dashboard!.budgetUtilisation.overall.progressPercent))"
              :style="{ width: clampedWidth(dashboard!.budgetUtilisation.overall.progressPercent) + '%' }"
            />
          </div>

          <div
            v-if="budgetedCategories(dashboard!.budgetUtilisation.categories).length"
            data-testid="dashboard-budget-categories"
            class="flex flex-col gap-4"
          >
            <div v-for="b in budgetedCategories(dashboard!.budgetUtilisation.categories)" :key="b.categoryId" class="flex items-center gap-3">
              <span class="w-2 h-2 rounded-full shrink-0" :class="statusDotClass(numberOrNull(b.progressPercent))" />
              <div class="w-7 h-7 rounded-lg bg-surface-50 flex items-center justify-center shrink-0">
                <i :class="['pi', categoryIconClass(b.categoryIcon)]" class="text-surface-500 text-xs" />
              </div>
              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium text-surface-900 truncate">{{ b.categoryName }}</p>
                <p class="text-xs text-surface-500 tabular-nums">
                  <MoneyDisplay :amount="b.spent" /> of <MoneyDisplay :amount="b.amount!" />
                </p>
              </div>
              <span class="text-xs font-semibold tabular-nums shrink-0" :class="statusColorClass(numberOrNull(b.progressPercent))">
                {{ numberOrNull(b.progressPercent) !== null ? Math.round(numberOrNull(b.progressPercent)!) + '%' : '' }}
              </span>
            </div>
          </div>
          <p v-else class="text-sm text-surface-400">No category budgets set yet.</p>
        </div>
      </div>
    </div>
  </AppLayout>
</template>
