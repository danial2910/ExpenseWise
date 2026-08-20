<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Chart from 'primevue/chart'
import AppLayout from '../../layouts/AppLayout.vue'
import ErrorState from '../../components/common/ErrorState.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import ChartTooltip from '../../components/common/ChartTooltip.vue'
import { useExternalTooltip } from '../../lib/chartTooltip'
import { fetchAdminDashboard } from '../../api/admin'
import { FEATURE_LABELS } from '../../types/admin'
import type { AdminDashboardResponse } from '../../types/admin'

type LoadState = 'loading' | 'error' | 'ready'

const MONTHS = 6

const loadState = ref<LoadState>('loading')
const dashboard = ref<AdminDashboardResponse | null>(null)

// Chart.js draws to a <canvas>, so it needs literal color strings — these
// hidden elements exist only so we can read the *resolved* value of design
// tokens (Tailwind's primary-400/500 utility classes) rather than
// hardcoding hex here.
const primaryColorRef = ref<HTMLElement | null>(null)
const primaryMutedRef = ref<HTMLElement | null>(null)
const primaryColor = ref('')
const primaryMutedColor = ref('')

const { tooltipState: signupsTooltip, externalTooltipHandler: signupsTooltipHandler } = useExternalTooltip()
const { tooltipState: activityTooltip, externalTooltipHandler: activityTooltipHandler } = useExternalTooltip()

onMounted(() => {
  if (primaryColorRef.value) primaryColor.value = getComputedStyle(primaryColorRef.value).color
  if (primaryMutedRef.value) primaryMutedColor.value = getComputedStyle(primaryMutedRef.value).color
  loadDashboard()
})

async function loadDashboard() {
  loadState.value = 'loading'
  try {
    dashboard.value = await fetchAdminDashboard(MONTHS)
    loadState.value = 'ready'
  } catch {
    loadState.value = 'error'
  }
}

const isEmpty = computed(() => loadState.value === 'ready' && (dashboard.value?.summary.totalUsers ?? 0) <= 1)

const sparklineOptions = {
  plugins: { legend: { display: false }, tooltip: { enabled: false } },
  scales: { x: { display: false }, y: { display: false } },
  elements: { point: { radius: 0 }, line: { borderWidth: 2, tension: 0.35 } },
  responsive: true,
  maintainAspectRatio: false,
}

const newSignupsSparkline = computed(() => {
  const points = dashboard.value?.signupsOverTime ?? []
  if (points.length < 2) return null
  return {
    labels: points.map((p) => p.month),
    datasets: [{ data: points.map((p) => p.count), borderColor: primaryColor.value, fill: false }],
  }
})

const summaryCards = computed(() => {
  const summary = dashboard.value?.summary
  if (!summary) return []
  return [
    { testid: 'summary-total-users', label: 'Total users', icon: 'pi-users', value: summary.totalUsers.toLocaleString(), tone: 'text-surface-900' },
    {
      testid: 'summary-active-disabled',
      label: 'Active / Disabled',
      icon: 'pi-shield',
      value: `${summary.activeUsers.toLocaleString()} / ${summary.disabledUsers.toLocaleString()}`,
      tone: 'text-surface-900',
    },
    {
      testid: 'summary-new-this-month',
      label: 'New this month',
      icon: 'pi-user-plus',
      value: `+${summary.newUsersThisMonth.toLocaleString()}`,
      tone: 'text-success',
      sparkline: newSignupsSparkline.value,
    },
  ]
})

function monthLabel(month: string): string {
  return new Date(`${month}T00:00:00`).toLocaleDateString('en-GB', { month: 'short' })
}

// Same filled-area line style as the user Dashboard's Income vs Expense
// chart (DashboardView.vue's trendChartData) — smooth curve, soft fill
// under the line, points only appear on hover.
function lineChartData(points: { month: string; count: number }[], color: string) {
  return {
    labels: points.map((p) => monthLabel(p.month)),
    datasets: [
      {
        label: 'Count',
        data: points.map((p) => p.count),
        borderColor: color,
        backgroundColor: color.replace('rgb', 'rgba').replace(')', ', 0.12)'),
        fill: true,
        tension: 0.35,
        pointRadius: 0,
        pointHoverRadius: 4,
        borderWidth: 2,
      },
    ],
  }
}

function chartOptions(externalTooltipHandler: (context: unknown) => void) {
  return {
    plugins: { legend: { display: false }, tooltip: { enabled: false, external: externalTooltipHandler } },
    scales: { x: { grid: { display: false } }, y: { display: false } },
    responsive: true,
    maintainAspectRatio: false,
  }
}
const signupsChartOptions = chartOptions(signupsTooltipHandler)
const activityChartOptions = chartOptions(activityTooltipHandler)

const signupsChartData = computed(() =>
  dashboard.value ? lineChartData(dashboard.value.signupsOverTime, primaryColor.value) : null,
)
const activityChartData = computed(() =>
  dashboard.value ? lineChartData(dashboard.value.activityOverTime, primaryMutedColor.value) : null,
)

const signupsTotal = computed(() =>
  (dashboard.value?.signupsOverTime.reduce((sum, p) => sum + p.count, 0) ?? 0).toLocaleString(),
)
const activityTotal = computed(() =>
  (dashboard.value?.activityOverTime.reduce((sum, p) => sum + p.count, 0) ?? 0).toLocaleString(),
)

function initials(name: string): string {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')
}

function joinedDisplay(iso: string): string {
  return new Date(iso).toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })
}
</script>

<template>
  <AppLayout title="Admin · Dashboard">
    <span ref="primaryColorRef" class="text-primary-400 hidden" aria-hidden="true"></span>
    <span ref="primaryMutedRef" class="text-primary-700 hidden" aria-hidden="true"></span>

    <ChartTooltip :state="signupsTooltip" />
    <ChartTooltip :state="activityTooltip" />

    <div class="flex flex-col gap-6">
      <div>
        <h1 class="font-display text-2xl font-semibold tracking-tight text-surface-900">Admin Dashboard</h1>
        <p class="text-sm text-surface-500 mt-1">System-wide usage across all accounts</p>
      </div>

      <ErrorState
        v-if="loadState === 'error'"
        testid="admin-dashboard-error-state"
        retry-testid="admin-dashboard-retry-button"
        title="Couldn't load admin analytics"
        description="Something went wrong while fetching usage data. Try again."
        class="bg-surface-0 border border-surface-300 rounded-xl"
        @retry="loadDashboard"
      />

      <!-- loading state -->
      <div v-else-if="loadState === 'loading'" data-testid="admin-dashboard-loading-skeleton" class="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div v-for="n in 3" :key="n" class="lg:col-span-4 bg-surface-0 border border-surface-300 rounded-xl p-5 flex flex-col gap-2">
          <div class="w-24 h-3 rounded bg-surface-200 animate-pulse" />
          <div class="w-28 h-7 rounded bg-surface-200 animate-pulse" />
        </div>
        <div class="lg:col-span-6 bg-surface-0 border border-surface-300 rounded-xl p-6">
          <div class="w-full h-48 rounded bg-surface-200 animate-pulse" />
        </div>
        <div class="lg:col-span-6 bg-surface-0 border border-surface-300 rounded-xl p-6">
          <div class="w-full h-48 rounded bg-surface-200 animate-pulse" />
        </div>
      </div>

      <EmptyState
        v-else-if="isEmpty"
        testid="admin-dashboard-empty-state"
        icon="pi-chart-bar"
        title="No usage data yet"
        description="Once users sign up and start recording transactions, system-wide analytics will appear here."
        class="bg-surface-0 border border-surface-300 rounded-xl"
      />

      <!-- ready state -->
      <div v-else data-testid="admin-dashboard-content" class="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div
          v-for="card in summaryCards"
          :key="card.testid"
          :data-testid="card.testid"
          class="lg:col-span-4 bg-surface-0 border border-surface-300 rounded-xl p-4 md:p-5 flex flex-col gap-3"
        >
          <div class="flex items-center gap-2">
            <div class="w-7 h-7 rounded-lg bg-surface-50 flex items-center justify-center text-surface-600 shrink-0">
              <i :class="['pi', card.icon]" class="text-xs" />
            </div>
            <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide truncate">{{ card.label }}</span>
          </div>
          <div class="flex items-end justify-between gap-3">
            <span class="text-2xl font-bold tabular-nums font-display" :class="card.tone">{{ card.value }}</span>
            <div v-if="'sparkline' in card && card.sparkline" class="w-16 h-8 shrink-0">
              <Chart type="line" :data="card.sparkline" :options="sparklineOptions" class="w-full h-full" />
            </div>
          </div>
        </div>

        <div class="lg:col-span-6 bg-surface-0 border border-surface-300 rounded-xl p-4 lg:p-6">
          <div class="flex items-baseline justify-between mb-5">
            <span class="text-sm font-semibold text-surface-900">User Signups — last {{ MONTHS }} months</span>
            <span class="text-sm text-surface-500">
              Total <span data-testid="signups-total" class="font-bold text-surface-900 tabular-nums">{{ signupsTotal }}</span>
            </span>
          </div>
          <div data-testid="signups-chart" class="h-36 md:h-40 lg:h-52">
            <Chart v-if="signupsChartData" type="line" :data="signupsChartData" :options="signupsChartOptions" class="h-full" />
          </div>
        </div>

        <div class="lg:col-span-6 bg-surface-0 border border-surface-300 rounded-xl p-4 lg:p-6">
          <div class="flex items-baseline justify-between mb-5">
            <span class="text-sm font-semibold text-surface-900">Transactions Recorded — last {{ MONTHS }} months</span>
            <span class="text-sm text-surface-500">
              Total <span data-testid="activity-total" class="font-bold text-surface-900 tabular-nums">{{ activityTotal }}</span>
            </span>
          </div>
          <div data-testid="activity-chart" class="h-36 md:h-40 lg:h-52">
            <Chart v-if="activityChartData" type="line" :data="activityChartData" :options="activityChartOptions" class="h-full" />
          </div>
        </div>

        <div class="lg:col-span-6 bg-surface-0 border border-surface-300 rounded-xl p-4 lg:p-6">
          <span class="text-sm font-semibold text-surface-900 block mb-5">Feature Usage — % of users active</span>
          <div data-testid="feature-usage-list" class="flex flex-col gap-4">
            <div v-for="usage in dashboard?.featureUsage" :key="usage.feature" :data-testid="`feature-usage-${usage.feature}`">
              <div class="flex items-center justify-between mb-1.5">
                <span class="text-sm font-medium text-surface-700">{{ FEATURE_LABELS[usage.feature] }}</span>
                <span class="text-sm font-semibold text-surface-900 tabular-nums">{{ usage.percentage }}%</span>
              </div>
              <div class="w-full h-2 rounded-full bg-surface-200 overflow-hidden">
                <div class="h-full rounded-full bg-primary-500" :style="{ width: `${usage.percentage}%` }" />
              </div>
            </div>
          </div>
        </div>

        <div class="lg:col-span-6 bg-surface-0 border border-surface-300 rounded-xl overflow-hidden">
          <div class="px-4 md:px-6 py-3 md:py-4 border-b border-surface-200 text-sm font-semibold text-surface-900">Recent Signups</div>
          <div data-testid="recent-signups-list">
            <div
              v-for="signup in dashboard?.recentSignups"
              :key="signup.id"
              :data-testid="`recent-signup-${signup.id}`"
              class="flex items-center gap-3 px-4 md:px-6 py-3 border-b border-surface-100 last:border-b-0"
            >
              <div class="w-8 h-8 rounded-lg bg-primary-50 text-primary-300 text-xs font-semibold flex items-center justify-center shrink-0">
                {{ initials(signup.fullName) }}
              </div>
              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium text-surface-900 truncate">{{ signup.fullName }}</p>
                <p class="text-xs text-surface-400 truncate">{{ signup.email }}</p>
              </div>
              <span class="text-xs text-surface-500 shrink-0">{{ joinedDisplay(signup.createdAt) }}</span>
            </div>
            <div v-if="!dashboard?.recentSignups.length" class="px-6 py-8 text-sm text-surface-500 text-center">
              No signups yet.
            </div>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>
