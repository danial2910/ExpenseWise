<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { isAxiosError } from 'axios'
import Button from 'primevue/button'
import Chart from 'primevue/chart'
import AppLayout from '../layouts/AppLayout.vue'
import FormError from '../components/common/FormError.vue'
import MoneyDisplay from '../components/common/MoneyDisplay.vue'
import { fetchReport, downloadReport, openReportPdfForPrint } from '../api/reports'
import type { ApiErrorResponse } from '../types/auth'
import type { ReportFormat, ReportResponse, ReportType } from '../types/report'

type LoadState = 'loading' | 'error' | 'ready'
type ExportAction = 'pdf' | 'excel' | 'print' | null

const MONTHS = 6

// Design-token-only category palette — indigo (primary) fading to slate
// (surface), matching "ExpenseWise Reports"'s own bar-color progression.
// Chart.js draws to a <canvas> and needs literal color strings, so these
// hidden elements exist purely so the *resolved* value of each Tailwind
// utility class can be read at mount, same technique as Dashboard/Admin
// Dashboard — the class name is still the single source of truth.
const PALETTE_CLASSES = [
  'text-primary-600',
  'text-primary-500',
  'text-surface-700',
  'text-surface-500',
  'text-primary-400',
  'text-surface-400',
  'text-primary-200',
  'text-primary-300',
  'text-surface-300',
]
const paletteRefs = ref<(Element | null)[]>([])
const paletteColors = ref<string[]>([])
const greenRef = ref<HTMLElement | null>(null)
const redRef = ref<HTMLElement | null>(null)
const greenColor = ref('')
const redColor = ref('')

function setPaletteRef(el: Element | null, index: number) {
  paletteRefs.value[index] = el
}

const now = new Date()
const reportType = ref<ReportType>('MONTHLY')
const selectedYear = ref(now.getFullYear())
const selectedMonth = ref(now.getMonth() + 1)

const loadState = ref<LoadState>('loading')
const report = ref<ReportResponse | null>(null)
const exporting = ref<ExportAction>(null)
const exportError = ref('')

onMounted(() => {
  paletteColors.value = paletteRefs.value.map((el) => (el ? getComputedStyle(el).color : '#94A3B8'))
  if (greenRef.value) greenColor.value = getComputedStyle(greenRef.value).color
  if (redRef.value) redColor.value = getComputedStyle(redRef.value).color
  loadReport()
})

async function loadReport() {
  loadState.value = 'loading'
  try {
    report.value = await fetchReport({
      type: reportType.value,
      year: selectedYear.value,
      month: reportType.value === 'MONTHLY' ? selectedMonth.value : undefined,
    })
    loadState.value = 'ready'
  } catch {
    loadState.value = 'error'
  }
}

function setReportType(type: ReportType) {
  if (reportType.value === type) return
  reportType.value = type
  loadReport()
}

const isCurrentPeriod = computed(() => {
  if (reportType.value === 'MONTHLY') {
    return selectedYear.value === now.getFullYear() && selectedMonth.value === now.getMonth() + 1
  }
  return selectedYear.value === now.getFullYear()
})

function prevPeriod() {
  if (reportType.value === 'MONTHLY') {
    if (selectedMonth.value === 1) {
      selectedMonth.value = 12
      selectedYear.value -= 1
    } else {
      selectedMonth.value -= 1
    }
  } else {
    selectedYear.value -= 1
  }
  loadReport()
}

function nextPeriod() {
  if (isCurrentPeriod.value) return
  if (reportType.value === 'MONTHLY') {
    if (selectedMonth.value === 12) {
      selectedMonth.value = 1
      selectedYear.value += 1
    } else {
      selectedMonth.value += 1
    }
  } else {
    selectedYear.value += 1
  }
  loadReport()
}

const periodLabel = computed(() => {
  if (reportType.value === 'MONTHLY') {
    return new Date(selectedYear.value, selectedMonth.value - 1, 1).toLocaleDateString('en-MY', {
      month: 'short',
      year: 'numeric',
    })
  }
  return String(selectedYear.value)
})

const isEmpty = computed(() => loadState.value === 'ready' && (report.value?.transactions.length ?? 0) === 0)

function toNumber(value: number | string): number {
  return typeof value === 'number' ? value : Number(value)
}

const netBalance = computed(() => (report.value ? toNumber(report.value.netBalance) : 0))
const netColorClass = computed(() => (netBalance.value >= 0 ? 'text-green-700' : 'text-red-600'))
const netPrefix = computed(() => (netBalance.value >= 0 ? '+' : '−'))

const categoryLegend = computed(() => {
  const categories = report.value?.categoryBreakdown ?? []
  return categories.map((c, i) => ({
    categoryId: c.categoryId,
    name: c.categoryName,
    amount: c.amount,
    percentage: Math.round(toNumber(c.percentage)),
    color: paletteColors.value[i % paletteColors.value.length],
  }))
})

const chartTitle = computed(() =>
  reportType.value === 'MONTHLY' ? `Income vs Expense — last ${MONTHS} months` : 'Income vs Expense by month',
)

function monthLabel(month: string): string {
  return new Date(`${month}T00:00:00`).toLocaleDateString('en-GB', { month: 'short' })
}

const trendChartData = computed(() => {
  const points = report.value?.monthlyTrend ?? []
  return {
    labels: points.map((p) => monthLabel(p.month)),
    datasets: [
      { label: 'Income', data: points.map((p) => toNumber(p.income)), backgroundColor: greenColor.value, borderRadius: 3 },
      { label: 'Expense', data: points.map((p) => toNumber(p.expense)), backgroundColor: redColor.value, borderRadius: 3 },
    ],
  }
})

const trendChartOptions = {
  plugins: { legend: { display: false } },
  scales: { x: { grid: { display: false } }, y: { display: false } },
  responsive: true,
  maintainAspectRatio: false,
}

function reportParams(format?: ReportFormat) {
  return {
    type: reportType.value,
    year: selectedYear.value,
    month: reportType.value === 'MONTHLY' ? selectedMonth.value : undefined,
    ...(format ? { format } : {}),
  }
}

async function onExport(format: ReportFormat) {
  exportError.value = ''
  exporting.value = format
  try {
    await downloadReport(reportParams(format) as Parameters<typeof downloadReport>[0])
  } catch (error) {
    exportError.value = extractErrorMessage(error, 'Could not generate the report. Please try again.')
  } finally {
    exporting.value = null
  }
}

async function onPrint() {
  exportError.value = ''
  exporting.value = 'print'
  try {
    await openReportPdfForPrint(reportParams())
  } catch (error) {
    exportError.value = extractErrorMessage(error, 'Could not open the report for printing.')
  } finally {
    exporting.value = null
  }
}

function extractErrorMessage(error: unknown, fallback: string): string {
  if (isAxiosError<ApiErrorResponse>(error) && error.response?.data.message) {
    return error.response.data.message
  }
  return fallback
}
</script>

<template>
  <AppLayout title="Reports">
    <span
      v-for="(cls, i) in PALETTE_CLASSES"
      :key="cls"
      :ref="(el) => setPaletteRef(el as Element | null, i)"
      :class="cls"
      class="hidden"
      aria-hidden="true"
    ></span>
    <span ref="greenRef" class="text-green-700 hidden" aria-hidden="true"></span>
    <span ref="redRef" class="text-red-600 hidden" aria-hidden="true"></span>

    <div class="flex flex-col gap-6">
      <div>
        <h1 class="text-2xl font-bold text-surface-900">Reports</h1>
        <p class="text-sm text-surface-500 mt-1">Generate and export spending reports</p>
      </div>

      <div class="flex items-center gap-3 flex-wrap">
        <div data-testid="report-type-toggle" class="flex items-center gap-0.5 bg-surface-100 rounded-lg p-0.5">
          <button
            data-testid="report-type-monthly"
            class="px-4 py-2 rounded-md text-sm font-semibold"
            :class="reportType === 'MONTHLY' ? 'bg-white text-surface-900 shadow-sm' : 'text-surface-500'"
            @click="setReportType('MONTHLY')"
          >
            Monthly
          </button>
          <button
            data-testid="report-type-yearly"
            class="px-4 py-2 rounded-md text-sm font-semibold"
            :class="reportType === 'YEARLY' ? 'bg-white text-surface-900 shadow-sm' : 'text-surface-500'"
            @click="setReportType('YEARLY')"
          >
            Yearly
          </button>
        </div>

        <div class="flex items-center gap-1.5 bg-white border border-surface-200 rounded-lg px-2 py-1.5">
          <button
            data-testid="report-prev-period-button"
            class="w-7 h-7 rounded-md flex items-center justify-center text-surface-600 hover:bg-surface-100"
            @click="prevPeriod"
          >
            <i class="pi pi-chevron-left text-xs" />
          </button>
          <span data-testid="report-period-label" class="text-sm font-semibold text-surface-900 min-w-[100px] text-center">
            {{ periodLabel }}
          </span>
          <button
            data-testid="report-next-period-button"
            class="w-7 h-7 rounded-md flex items-center justify-center"
            :class="isCurrentPeriod ? 'text-surface-300 cursor-not-allowed' : 'text-surface-600 hover:bg-surface-100'"
            :disabled="isCurrentPeriod"
            @click="nextPeriod"
          >
            <i class="pi pi-chevron-right text-xs" />
          </button>
        </div>

        <div class="flex-1"></div>

        <div v-if="loadState === 'ready' && !isEmpty" class="flex items-center gap-2">
          <Button
            data-testid="report-export-pdf-button"
            label="PDF"
            icon="pi pi-file-pdf"
            severity="secondary"
            outlined
            :loading="exporting === 'pdf'"
            @click="onExport('pdf')"
          />
          <Button
            data-testid="report-export-excel-button"
            label="Excel"
            icon="pi pi-file-excel"
            severity="secondary"
            outlined
            :loading="exporting === 'excel'"
            @click="onExport('excel')"
          />
          <Button
            data-testid="report-print-button"
            label="Print"
            icon="pi pi-print"
            severity="secondary"
            outlined
            :loading="exporting === 'print'"
            @click="onPrint"
          />
        </div>
      </div>

      <FormError v-if="exportError" :message="exportError" testid="report-export-error-banner" />

      <!-- error state -->
      <div
        v-if="loadState === 'error'"
        data-testid="reports-error-state"
        class="flex flex-col items-center justify-center gap-4 py-20 px-8 bg-white border border-surface-200 rounded-lg"
      >
        <div class="w-14 h-14 rounded-lg bg-red-50 flex items-center justify-center">
          <i class="pi pi-exclamation-triangle text-red-600 text-2xl" />
        </div>
        <p class="text-base font-semibold text-surface-900">Couldn't generate this report</p>
        <p class="text-sm text-surface-500 text-center max-w-sm">
          Something went wrong while fetching your data. Check your connection and try again.
        </p>
        <Button data-testid="reports-retry-button" label="Retry" icon="pi pi-refresh" @click="loadReport" />
      </div>

      <!-- loading state -->
      <div
        v-else-if="loadState === 'loading'"
        data-testid="reports-loading-skeleton"
        class="flex flex-col items-center justify-center gap-4 py-20 px-8 bg-white border border-surface-200 rounded-lg"
      >
        <div class="w-9 h-9 border-[3px] border-surface-200 border-t-primary-600 rounded-full animate-spin" />
        <p class="text-base font-semibold text-surface-900">Generating your report</p>
        <p class="text-xs text-surface-500">This can take up to 10 seconds</p>
      </div>

      <!-- empty state -->
      <div
        v-else-if="isEmpty"
        data-testid="reports-empty-state"
        class="flex flex-col items-center justify-center gap-4 py-20 px-8 bg-white border border-surface-200 rounded-lg"
      >
        <div class="w-14 h-14 rounded-lg bg-surface-100 flex items-center justify-center">
          <i class="pi pi-chart-bar text-surface-400 text-2xl" />
        </div>
        <p class="text-base font-semibold text-surface-900">No data for {{ periodLabel }}</p>
        <p class="text-sm text-surface-500 text-center max-w-sm">
          There are no transactions recorded for this period yet. Try a different period or add a transaction.
        </p>
      </div>

      <!-- ready state -->
      <div v-else data-testid="reports-content" class="flex flex-col gap-6">
        <div class="grid grid-cols-3 gap-4">
          <div data-testid="summary-income" class="bg-white border border-surface-200 rounded-lg p-5">
            <p class="text-xs font-semibold text-surface-500 uppercase tracking-wide">Total Income</p>
            <p class="text-[22px] font-bold text-green-700 mt-1.5 tabular-nums">
              + <MoneyDisplay :amount="report!.totalIncome" />
            </p>
          </div>
          <div data-testid="summary-expense" class="bg-white border border-surface-200 rounded-lg p-5">
            <p class="text-xs font-semibold text-surface-500 uppercase tracking-wide">Total Expenses</p>
            <p class="text-[22px] font-bold text-red-600 mt-1.5 tabular-nums">
              &minus; <MoneyDisplay :amount="report!.totalExpense" />
            </p>
          </div>
          <div data-testid="summary-net" class="bg-white border border-surface-200 rounded-lg p-5">
            <p class="text-xs font-semibold text-surface-500 uppercase tracking-wide">Net Savings</p>
            <p class="text-[22px] font-bold mt-1.5 tabular-nums" :class="netColorClass">
              {{ netPrefix }} <MoneyDisplay :amount="Math.abs(netBalance)" />
            </p>
          </div>
        </div>

        <div class="grid grid-cols-12 gap-6">
          <div data-testid="report-category-breakdown" class="col-span-7 bg-white border border-surface-200 rounded-lg overflow-hidden">
            <div class="px-5 py-4 border-b border-surface-200 text-sm font-semibold text-surface-900">Category Breakdown</div>
            <div class="grid grid-cols-[1fr_120px_100px_140px] px-5 py-2.5 bg-surface-50 border-b border-surface-200">
              <span class="text-[11px] font-semibold text-surface-500 uppercase tracking-wide">Category</span>
              <span class="text-[11px] font-semibold text-surface-500 uppercase tracking-wide text-right">Spent</span>
              <span class="text-[11px] font-semibold text-surface-500 uppercase tracking-wide text-right">Share</span>
              <span class="text-[11px] font-semibold text-surface-500 uppercase tracking-wide pl-4">Distribution</span>
            </div>
            <div v-if="!categoryLegend.length" class="px-5 py-8 text-sm text-surface-500 text-center">
              No expenses recorded for this period.
            </div>
            <div
              v-for="c in categoryLegend"
              :key="c.categoryId"
              :data-testid="`report-category-row-${c.categoryId}`"
              class="grid grid-cols-[1fr_120px_100px_140px] px-5 py-3 border-b border-surface-100 items-center last:border-b-0"
            >
              <span class="text-sm font-medium text-surface-900">{{ c.name }}</span>
              <span class="text-sm text-surface-700 text-right tabular-nums"><MoneyDisplay :amount="c.amount" /></span>
              <span class="text-sm text-surface-500 text-right tabular-nums">{{ c.percentage }}%</span>
              <span class="pl-4">
                <span class="block w-full h-1.5 rounded-full bg-surface-200 overflow-hidden">
                  <span class="block h-full rounded-full" :style="{ width: c.percentage + '%', backgroundColor: c.color }" />
                </span>
              </span>
            </div>
          </div>

          <div class="col-span-5 bg-white border border-surface-200 rounded-lg p-6">
            <p class="text-sm font-semibold text-surface-900 mb-5">{{ chartTitle }}</p>
            <div data-testid="report-trend-chart" class="h-52">
              <Chart type="bar" :data="trendChartData" :options="trendChartOptions" class="h-full" />
            </div>
            <div class="flex items-center gap-4 mt-4">
              <span class="flex items-center gap-1.5 text-xs text-surface-500">
                <span class="w-2 h-2 rounded-sm bg-green-700" />Income
              </span>
              <span class="flex items-center gap-1.5 text-xs text-surface-500">
                <span class="w-2 h-2 rounded-sm bg-red-600" />Expense
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>
