<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { isAxiosError } from 'axios'
import Button from 'primevue/button'
import Chart from 'primevue/chart'
import AppLayout from '../layouts/AppLayout.vue'
import FormError from '../components/common/FormError.vue'
import MoneyDisplay from '../components/common/MoneyDisplay.vue'
import EmptyState from '../components/common/EmptyState.vue'
import ErrorState from '../components/common/ErrorState.vue'
import LoadingState from '../components/common/LoadingState.vue'
import ChartTooltip from '../components/common/ChartTooltip.vue'
import { useExternalTooltip } from '../lib/chartTooltip'
import { fetchReport, downloadReport, openReportPdfForPrint } from '../api/reports'
import type { ApiErrorResponse } from '../types/auth'
import type { ReportFormat, ReportResponse, ReportType } from '../types/report'

type LoadState = 'loading' | 'error' | 'ready'
type ExportAction = 'pdf' | 'excel' | 'print' | null

const MONTHS = 6

// Design-token-only category palette — same cyan → indigo family as the
// Dashboard donut (Phase 3), not the old mixed primary/surface ramp. Chart.js
// draws to a <canvas> and needs literal color strings, so these hidden
// elements exist purely so the *resolved* value of each Tailwind utility
// class can be read at mount — the class name is still the single source of
// truth, never a hardcoded hex.
const PALETTE_CLASSES = [
  'text-primary-300',
  'text-primary-500',
  'text-primary-700',
  'text-aurora-teal',
  'text-aurora-blue',
  'text-aurora-indigo',
  'text-primary-400',
  'text-primary-600',
]
const paletteRefs = ref<(Element | null)[]>([])
const paletteColors = ref<string[]>([])
const successRef = ref<HTMLElement | null>(null)
const dangerRef = ref<HTMLElement | null>(null)
const successColor = ref('')
const dangerColor = ref('')

function setPaletteRef(el: Element | null, index: number) {
  paletteRefs.value[index] = el
}

const { tooltipState: trendTooltip, externalTooltipHandler: trendTooltipHandler } = useExternalTooltip()

const now = new Date()
const reportType = ref<ReportType>('MONTHLY')
const selectedYear = ref(now.getFullYear())
const selectedMonth = ref(now.getMonth() + 1)

const loadState = ref<LoadState>('loading')
const report = ref<ReportResponse | null>(null)
const exporting = ref<ExportAction>(null)
const exportError = ref('')
const exportSuccess = ref<ExportAction>(null)
let exportSuccessTimeout: ReturnType<typeof setTimeout> | undefined

onMounted(() => {
  paletteColors.value = paletteRefs.value.map((el) => (el ? getComputedStyle(el).color : '#5B6472'))
  if (successRef.value) successColor.value = getComputedStyle(successRef.value).color
  if (dangerRef.value) dangerColor.value = getComputedStyle(dangerRef.value).color
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
const netColorClass = computed(() => (netBalance.value >= 0 ? 'text-success' : 'text-danger'))
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

function flashExportSuccess(action: ExportAction) {
  exportSuccess.value = action
  clearTimeout(exportSuccessTimeout)
  exportSuccessTimeout = setTimeout(() => {
    exportSuccess.value = null
  }, 2500)
}

async function onExport(format: ReportFormat) {
  exportError.value = ''
  exporting.value = format
  try {
    await downloadReport(reportParams(format) as Parameters<typeof downloadReport>[0])
    flashExportSuccess(format)
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
    flashExportSuccess('print')
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
    <span ref="successRef" class="text-success hidden" aria-hidden="true"></span>
    <span ref="dangerRef" class="text-danger hidden" aria-hidden="true"></span>

    <ChartTooltip :state="trendTooltip" />

    <div class="flex flex-col gap-6">
      <div>
        <h1 class="font-display text-2xl font-semibold tracking-tight text-surface-900">Reports</h1>
        <p class="text-sm text-surface-500 mt-1">Generate and export spending reports</p>
      </div>

      <div class="flex items-center gap-3 flex-wrap">
        <div data-testid="report-type-toggle" class="flex items-center gap-0.5 bg-surface-50 rounded-lg p-0.5">
          <button
            data-testid="report-type-monthly"
            class="px-4 py-2 rounded-md text-sm font-semibold transition-colors duration-fast ease-out-expo"
            :class="reportType === 'MONTHLY' ? 'bg-surface-0 text-surface-900 shadow-soft-sm' : 'text-surface-500'"
            @click="setReportType('MONTHLY')"
          >
            Monthly
          </button>
          <button
            data-testid="report-type-yearly"
            class="px-4 py-2 rounded-md text-sm font-semibold transition-colors duration-fast ease-out-expo"
            :class="reportType === 'YEARLY' ? 'bg-surface-0 text-surface-900 shadow-soft-sm' : 'text-surface-500'"
            @click="setReportType('YEARLY')"
          >
            Yearly
          </button>
        </div>

        <div class="flex items-center gap-1.5 bg-surface-0 border border-surface-300 rounded-lg px-2 py-1.5">
          <button
            data-testid="report-prev-period-button"
            class="w-11 h-11 lg:w-7 lg:h-7 rounded-md flex items-center justify-center text-surface-600 hover:bg-surface-50 transition-colors duration-fast ease-out-expo"
            @click="prevPeriod"
          >
            <i class="pi pi-chevron-left text-xs" />
          </button>
          <span data-testid="report-period-label" class="text-sm font-semibold text-surface-900 min-w-[100px] text-center">
            {{ periodLabel }}
          </span>
          <button
            data-testid="report-next-period-button"
            class="w-11 h-11 lg:w-7 lg:h-7 rounded-md flex items-center justify-center transition-colors duration-fast ease-out-expo"
            :class="isCurrentPeriod ? 'text-surface-400 cursor-not-allowed' : 'text-surface-600 hover:bg-surface-50'"
            :disabled="isCurrentPeriod"
            @click="nextPeriod"
          >
            <i class="pi pi-chevron-right text-xs" />
          </button>
        </div>

        <div class="flex-1"></div>

        <div v-if="loadState === 'ready' && !isEmpty" class="flex items-center gap-2 flex-wrap">
          <Button
            data-testid="report-export-pdf-button"
            :label="exportSuccess === 'pdf' ? 'Downloaded' : 'PDF'"
            :icon="exportSuccess === 'pdf' ? 'pi pi-check' : 'pi pi-file-pdf'"
            severity="secondary"
            outlined
            :loading="exporting === 'pdf'"
            :disabled="exporting !== null && exporting !== 'pdf'"
            class="transition-transform duration-fast ease-out-expo active:scale-[0.97]"
            @click="onExport('pdf')"
          />
          <Button
            data-testid="report-export-excel-button"
            :label="exportSuccess === 'excel' ? 'Downloaded' : 'Excel'"
            :icon="exportSuccess === 'excel' ? 'pi pi-check' : 'pi pi-file-excel'"
            severity="secondary"
            outlined
            :loading="exporting === 'excel'"
            :disabled="exporting !== null && exporting !== 'excel'"
            class="transition-transform duration-fast ease-out-expo active:scale-[0.97]"
            @click="onExport('excel')"
          />
          <Button
            data-testid="report-print-button"
            :label="exportSuccess === 'print' ? 'Opened' : 'Print'"
            :icon="exportSuccess === 'print' ? 'pi pi-check' : 'pi pi-print'"
            severity="secondary"
            outlined
            :loading="exporting === 'print'"
            :disabled="exporting !== null && exporting !== 'print'"
            class="transition-transform duration-fast ease-out-expo active:scale-[0.97]"
            @click="onPrint"
          />
        </div>
      </div>

      <FormError v-if="exportError" :message="exportError" testid="report-export-error-banner" />

      <ErrorState
        v-if="loadState === 'error'"
        testid="reports-error-state"
        retry-testid="reports-retry-button"
        title="Couldn't generate this report"
        description="Something went wrong while fetching your data. Check your connection and try again."
        class="bg-surface-0 border border-surface-300 rounded-xl"
        @retry="loadReport"
      />

      <LoadingState
        v-else-if="loadState === 'loading'"
        testid="reports-loading-skeleton"
        label="Generating your report — this can take up to 10 seconds"
        class="bg-surface-0 border border-surface-300 rounded-xl"
      />

      <EmptyState
        v-else-if="isEmpty"
        testid="reports-empty-state"
        icon="pi-chart-bar"
        :title="`No data for ${periodLabel}`"
        description="There are no transactions recorded for this period yet. Try a different period or add a transaction."
        class="bg-surface-0 border border-surface-300 rounded-xl"
      />

      <!-- ready state -->
      <div v-else data-testid="reports-content" class="flex flex-col gap-6">
        <div class="grid grid-cols-3 gap-3 sm:gap-4">
          <div data-testid="summary-income" class="bg-surface-0 border border-surface-300 rounded-xl p-4 sm:p-5">
            <span class="block h-[3px] w-6 rounded-full bg-success" />
            <p class="text-[11px] sm:text-xs font-medium text-surface-500 mt-2.5">Total Income</p>
            <p class="text-base sm:text-lg lg:text-[22px] font-bold text-success mt-1.5 tabular-nums font-display">
              + <MoneyDisplay :amount="report!.totalIncome" />
            </p>
          </div>
          <div data-testid="summary-expense" class="bg-surface-0 border border-surface-300 rounded-xl p-4 sm:p-5">
            <span class="block h-[3px] w-6 rounded-full bg-danger" />
            <p class="text-[11px] sm:text-xs font-medium text-surface-500 mt-2.5">Total Expenses</p>
            <p class="text-base sm:text-lg lg:text-[22px] font-bold text-danger mt-1.5 tabular-nums font-display">
              &minus; <MoneyDisplay :amount="report!.totalExpense" />
            </p>
          </div>
          <div data-testid="summary-net" class="bg-surface-0 border border-surface-300 rounded-xl p-4 sm:p-5">
            <span class="block h-[3px] w-6 rounded-full bg-primary-500" />
            <p class="text-[11px] sm:text-xs font-medium text-surface-500 mt-2.5">Net Savings</p>
            <p class="text-base sm:text-lg lg:text-[22px] font-bold mt-1.5 tabular-nums font-display" :class="netColorClass">
              {{ netPrefix }} <MoneyDisplay :amount="Math.abs(netBalance)" />
            </p>
          </div>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <div
            data-testid="report-category-breakdown"
            class="order-2 lg:order-1 lg:col-span-7 bg-surface-0 border border-surface-300 rounded-xl overflow-hidden"
          >
            <div class="px-5 py-4 border-b border-surface-200 text-sm font-semibold text-surface-900">Category Breakdown</div>
            <div class="hidden lg:grid grid-cols-[1fr_120px_100px_140px] px-5 py-2.5 bg-surface-50 border-b border-surface-200">
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
              class="border-b border-surface-100 last:border-b-0"
            >
              <!-- desktop row -->
              <div class="hidden lg:grid grid-cols-[1fr_120px_100px_140px] px-5 py-3 items-center">
                <span class="text-sm font-medium text-surface-900">{{ c.name }}</span>
                <span class="text-sm text-surface-700 text-right tabular-nums"><MoneyDisplay :amount="c.amount" /></span>
                <span class="text-sm text-surface-500 text-right tabular-nums">{{ c.percentage }}%</span>
                <span class="pl-4">
                  <span class="block w-full h-1.5 rounded-full bg-surface-200 overflow-hidden">
                    <span class="block h-full rounded-full" :style="{ width: c.percentage + '%', backgroundColor: c.color }" />
                  </span>
                </span>
              </div>
              <!-- tablet/mobile row -->
              <div class="lg:hidden flex flex-col gap-1.5 px-5 py-3">
                <div class="flex items-center justify-between gap-3">
                  <span class="text-sm font-medium text-surface-900">{{ c.name }}</span>
                  <span class="text-xs text-surface-500 tabular-nums shrink-0">
                    <MoneyDisplay :amount="c.amount" /> · {{ c.percentage }}%
                  </span>
                </div>
                <span class="block w-full h-1.5 rounded-full bg-surface-200 overflow-hidden">
                  <span class="block h-full rounded-full" :style="{ width: c.percentage + '%', backgroundColor: c.color }" />
                </span>
              </div>
            </div>
          </div>

          <div class="order-1 lg:order-2 lg:col-span-5 bg-surface-0 border border-surface-300 rounded-xl p-4 lg:p-6">
            <p class="text-sm font-semibold text-surface-900 mb-5">{{ chartTitle }}</p>
            <div data-testid="report-trend-chart" class="h-40 lg:h-52">
              <Chart type="line" :data="trendChartData" :options="trendChartOptions" class="h-full" />
            </div>
            <div class="flex items-center gap-4 mt-4">
              <span class="flex items-center gap-1.5 text-xs text-surface-500">
                <span class="w-2 h-2 rounded-full bg-success" />Income
              </span>
              <span class="flex items-center gap-1.5 text-xs text-surface-500">
                <span class="w-2 h-2 rounded-full bg-danger" />Expense
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>
