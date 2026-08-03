<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { fetchMyEntitlements } from '../api/users'
import type { Feature } from '../types/admin'

withDefaults(defineProps<{ title?: string }>(), { title: 'Dashboard' })

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const entitlements = ref<Record<Feature, boolean> | null>(null)

onMounted(async () => {
  if (!authStore.isAdmin) {
    try {
      entitlements.value = await fetchMyEntitlements()
    } catch {
      entitlements.value = null
    }
  }
})

const ADMIN_NAV_ITEMS = [
  { testid: 'nav-admin-dashboard', label: 'Admin Dashboard', to: '/admin/dashboard', routeName: 'admin-dashboard' },
  { testid: 'nav-admin-users', label: 'User Management', to: '/admin/users', routeName: 'admin-users' },
  { testid: 'nav-ai-assistant', label: 'AI Assistant', to: '/ai-assistant', routeName: 'ai-assistant' },
  { testid: 'nav-profile', label: 'Profile', to: '/profile', routeName: 'profile' },
]

const USER_NAV_ITEMS: { testid: string; label: string; to: string; routeName: string; feature: Feature | null }[] = [
  { testid: 'nav-dashboard', label: 'Dashboard', to: '/dashboard', routeName: 'dashboard', feature: null },
  { testid: 'nav-transactions', label: 'Transactions', to: '/transactions', routeName: 'transactions', feature: 'TRANSACTIONS' },
  { testid: 'nav-categories', label: 'Categories', to: '/categories', routeName: 'categories', feature: 'CATEGORIES' },
  { testid: 'nav-budgets', label: 'Budgets', to: '/budgets', routeName: 'budgets', feature: 'BUDGETS' },
  { testid: 'nav-ai-assistant', label: 'AI Assistant', to: '/ai-assistant', routeName: 'ai-assistant', feature: 'AI_ASSISTANT' },
  { testid: 'nav-profile', label: 'Profile', to: '/profile', routeName: 'profile', feature: null },
]

const navItems = computed(() => {
  if (authStore.isAdmin) {
    return ADMIN_NAV_ITEMS
  }
  return USER_NAV_ITEMS.filter((item) => !item.feature || entitlements.value?.[item.feature] !== false)
})

const initials = computed(() => {
  const name = authStore.user?.fullName ?? ''
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')
})

async function onLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="min-h-screen bg-surface-100 flex">
    <aside class="w-60 shrink-0 bg-white border-r border-surface-200 flex flex-col py-4">
      <div class="flex items-center gap-2 px-4 pb-4 mb-2 border-b border-surface-100">
        <div class="w-7 h-7 rounded-lg bg-primary-600 shrink-0 flex items-center justify-center">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round">
            <path d="M4 17V9a2 2 0 0 1 2-2h9l5 5v5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2Z" />
            <path d="M15 7v4h4" />
          </svg>
        </div>
        <span class="text-base font-bold text-surface-900">ExpenseWise</span>
      </div>
      <nav class="flex flex-col gap-0.5 px-2">
        <router-link
          v-for="item in navItems"
          :key="item.routeName"
          :data-testid="item.testid"
          :to="item.to"
          class="flex items-center gap-3 px-2.5 py-2 rounded-lg text-sm font-semibold"
          :class="route.name === item.routeName
            ? 'bg-primary-50 text-primary-600'
            : 'text-surface-600 hover:bg-surface-50'"
        >
          {{ item.label }}
        </router-link>
      </nav>
    </aside>

    <div class="flex-1 flex flex-col min-w-0">
      <header class="h-16 shrink-0 flex items-center justify-between px-8 border-b border-surface-200 bg-white">
        <span class="text-base font-semibold text-surface-900">{{ title }}</span>
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <div class="w-8 h-8 rounded-lg bg-primary-100 text-primary-600 text-sm font-semibold flex items-center justify-center">
              {{ initials }}
            </div>
            <span data-testid="current-user-name" class="text-sm font-medium text-surface-900">
              {{ authStore.user?.fullName }}
            </span>
          </div>
          <button
            data-testid="logout-button"
            class="text-sm font-semibold text-surface-500 hover:text-surface-900"
            @click="onLogout"
          >
            Log out
          </button>
        </div>
      </header>

      <main class="flex-1 p-8">
        <slot />
      </main>
    </div>
  </div>
</template>
