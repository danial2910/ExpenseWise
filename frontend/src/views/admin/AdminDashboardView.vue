<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Button from 'primevue/button'
import Chart from 'primevue/chart'
import AppLayout from '../../layouts/AppLayout.vue'
import { fetchAdminDashboard } from '../../api/admin'
import { FEATURE_LABELS } from '../../types/admin'
import type { AdminDashboardResponse } from '../../types/admin'

type LoadState = 'loading' | 'error' | 'ready'

const MONTHS = 6

const loadState = ref<LoadState>('loading')
const dashboard = ref<AdminDashboardResponse | null>(null)

// Chart.js draws to a <canvas>, so it needs literal color strings — these
// two zero-size elements exist only so we can read the *resolved* value of
// design tokens (Tailwind's primary-600 / surface-700 utility classes)
// rather than hardcoding hex codes here.
const primaryColorRef = ref<HTMLElement | null>(null)
const slateColorRef = ref<HTMLElement | null>(null)
const primaryColor = ref('')
const slateColor = ref('')

onMounted(() => {
  if (primaryColorRef.value) {
    primaryColor.value = getComputedStyle(primaryColorRef.value).color
  }
  if (slateColorRef.value) {
    slateColor.value = getComputedStyle(slateColorRef.value).color
  }
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

const summaryCards = computed(() => {
  const summary = dashboard.value?.summary
  if (!summary) return []
  return [
    { testid: 'summary-total-users', label: 'Total users', value: summary.totalUsers.toLocaleString(), colorClass: 'text-surface-900' },
    {
      testid: 'summary-active-disabled',
      label: 'Active / Disabled',
      value: `${summary.activeUsers.toLocaleString()} / ${summary.disabledUsers.toLocaleString()}`,
      colorClass: 'text-surface-900',
    },
    {
      testid: 'summary-new-this-month',
      label: 'New this month',
      value: `+${summary.newUsersThisMonth.toLocaleString()}`,
      colorClass: 'text-green-700',
    },
  ]
})

function monthLabel(month: string): string {
  return new Date(`${month}T00:00:00`).toLocaleDateString('en-GB', { month: 'short' })
}

function lineChartData(points: { month: string; count: number }[], color: string) {
  return {
    labels: points.map((p) => monthLabel(p.month)),
    datasets: [
      {
        data: points.map((p) => p.count),
        borderColor: color,
        backgroundColor: color,
        borderWidth: 2.5,
        pointRadius: 3.5,
        tension: 0,
      },
    ],
  }
}

const chartOptions = {
  plugins: { legend: { display: false } },
  scales: {
    x: { grid: { display: false } },
    y: { display: false },
  },
  responsive: true,
  maintainAspectRatio: false,
}

const signupsChartData = computed(() =>
  dashboard.value ? lineChartData(dashboard.value.signupsOverTime, primaryColor.value) : null,
)
const activityChartData = computed(() =>
  dashboard.value ? lineChartData(dashboard.value.activityOverTime, slateColor.value) : null,
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
    <span ref="primaryColorRef" class="text-primary-600 hidden" aria-hidden="true"></span>
    <span ref="slateColorRef" class="text-surface-700 hidden" aria-hidden="true"></span>

    <div class="flex flex-col gap-6">
      <div>
        <h1 class="text-2xl font-bold text-surface-900">Admin Dashboard</h1>
        <p class="text-sm text-surface-500 mt-1">System-wide usage across all accounts</p>
      </div>

      <!-- error state -->
      <div
        v-if="loadState === 'error'"
        data-testid="admin-dashboard-error-state"
        class="flex flex-col items-center justify-center gap-4 py-14 px-6 lg:py-20 lg:px-8 bg-white border border-surface-200 rounded-lg"
      >
        <div class="w-14 h-14 rounded-lg bg-red-50 flex items-center justify-center">
          <i class="pi pi-exclamation-triangle text-red-600 text-2xl" />
        </div>
        <p class="text-base font-semibold text-surface-900">Couldn't load admin analytics</p>
        <p class="text-sm text-surface-500 text-center max-w-sm">Something went wrong while fetching usage data. Try again.</p>
        <Button data-testid="admin-dashboard-retry-button" label="Retry" icon="pi pi-refresh" @click="loadDashboard" />
      </div>

      <!-- loading state -->
      <div v-else-if="loadState === 'loading'" data-testid="admin-dashboard-loading-skeleton" class="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div v-for="n in 3" :key="n" class="lg:col-span-4 bg-white border border-surface-200 rounded-lg p-5 flex flex-col gap-2">
          <div class="w-24 h-3 rounded bg-surface-200 animate-pulse" />
          <div class="w-28 h-7 rounded bg-surface-200 animate-pulse" />
        </div>
        <div class="lg:col-span-6 bg-white border border-surface-200 rounded-lg p-6">
          <div class="w-full h-48 rounded bg-surface-200 animate-pulse" />
        </div>
        <div class="lg:col-span-6 bg-white border border-surface-200 rounded-lg p-6">
          <div class="w-full h-48 rounded bg-surface-200 animate-pulse" />
        </div>
      </div>

      <!-- empty state -->
      <div
        v-else-if="isEmpty"
        data-testid="admin-dashboard-empty-state"
        class="flex flex-col items-center justify-center gap-4 py-14 px-6 lg:py-20 lg:px-8 bg-white border border-surface-200 rounded-lg"
      >
        <div class="w-14 h-14 rounded-lg bg-surface-100 flex items-center justify-center">
          <i class="pi pi-chart-bar text-surface-400 text-2xl" />
        </div>
        <p class="text-base font-semibold text-surface-900">No usage data yet</p>
        <p class="text-sm text-surface-500 text-center max-w-sm">
          Once users sign up and start recording transactions, system-wide analytics will appear here.
        </p>
      </div>

      <!-- ready state -->
      <div v-else data-testid="admin-dashboard-content" class="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div
          v-for="card in summaryCards"
          :key="card.testid"
          :data-testid="card.testid"
          class="lg:col-span-4 bg-white border border-surface-200 rounded-lg p-4 md:p-5"
        >
          <!-- desktop/tablet: label above value -->
          <div class="hidden md:flex flex-col gap-2">
            <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide">{{ card.label }}</span>
            <span class="text-2xl font-bold tabular-nums" :class="card.colorClass">{{ card.value }}</span>
          </div>
          <!-- mobile: label and value side by side -->
          <div class="md:hidden flex items-center justify-between">
            <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide">{{ card.label }}</span>
            <span class="text-lg font-bold tabular-nums" :class="card.colorClass">{{ card.value }}</span>
          </div>
        </div>

        <div class="lg:col-span-6 bg-white border border-surface-200 rounded-lg p-4 lg:p-6">
          <div class="flex items-baseline justify-between mb-5">
            <span class="text-sm font-semibold text-surface-900">User Signups — last {{ MONTHS }} months</span>
            <span class="text-sm text-surface-500">
              Total <span data-testid="signups-total" class="font-bold text-surface-900 tabular-nums">{{ signupsTotal }}</span>
            </span>
          </div>
          <div data-testid="signups-chart" class="h-36 md:h-40 lg:h-52">
            <Chart v-if="signupsChartData" type="line" :data="signupsChartData" :options="chartOptions" class="h-full" />
          </div>
        </div>

        <div class="lg:col-span-6 bg-white border border-surface-200 rounded-lg p-4 lg:p-6">
          <div class="flex items-baseline justify-between mb-5">
            <span class="text-sm font-semibold text-surface-900">Transactions Recorded — last {{ MONTHS }} months</span>
            <span class="text-sm text-surface-500">
              Total <span data-testid="activity-total" class="font-bold text-surface-900 tabular-nums">{{ activityTotal }}</span>
            </span>
          </div>
          <div data-testid="activity-chart" class="h-36 md:h-40 lg:h-52">
            <Chart v-if="activityChartData" type="line" :data="activityChartData" :options="chartOptions" class="h-full" />
          </div>
        </div>

        <div class="lg:col-span-6 bg-white border border-surface-200 rounded-lg p-4 lg:p-6">
          <span class="text-sm font-semibold text-surface-900 block mb-5">Feature Usage — % of users active</span>
          <div data-testid="feature-usage-list" class="flex flex-col gap-4">
            <div v-for="usage in dashboard?.featureUsage" :key="usage.feature" :data-testid="`feature-usage-${usage.feature}`">
              <div class="flex items-center justify-between mb-1.5">
                <span class="text-sm font-medium text-surface-700">{{ FEATURE_LABELS[usage.feature] }}</span>
                <span class="text-sm font-semibold text-surface-900 tabular-nums">{{ usage.percentage }}%</span>
              </div>
              <div class="w-full h-2 rounded bg-surface-200 overflow-hidden">
                <div class="h-full rounded bg-primary-600" :style="{ width: `${usage.percentage}%` }" />
              </div>
            </div>
          </div>
        </div>

        <div class="lg:col-span-6 bg-white border border-surface-200 rounded-lg overflow-hidden">
          <div class="px-4 md:px-6 py-3 md:py-4 border-b border-surface-200 text-sm font-semibold text-surface-900">Recent Signups</div>
          <div data-testid="recent-signups-list">
            <div
              v-for="signup in dashboard?.recentSignups"
              :key="signup.id"
              :data-testid="`recent-signup-${signup.id}`"
              class="flex items-center gap-3 px-4 md:px-6 py-3 border-b border-surface-100 last:border-b-0"
            >
              <div class="w-8 h-8 rounded-lg bg-primary-50 text-primary-600 text-xs font-semibold flex items-center justify-center shrink-0">
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
