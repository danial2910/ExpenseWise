<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import Drawer from 'primevue/drawer'
import { useAuthStore } from '../stores/auth'
import { fetchMyEntitlements } from '../api/users'
import logoIcon from '../assets/logo-icon.png'
import type { Feature } from '../types/admin'

const props = withDefaults(
  defineProps<{ title?: string; fabLabel?: string; fabTo?: string }>(),
  { title: 'Dashboard', fabLabel: undefined, fabTo: undefined },
)
const emit = defineEmits<{ fab: [] }>()

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const moreSheetOpen = ref(false)

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
  { testid: 'nav-admin-dashboard', label: 'Admin Dashboard', to: '/admin/dashboard', routeName: 'admin-dashboard', icon: 'pi-th-large' },
  { testid: 'nav-admin-users', label: 'User Management', to: '/admin/users', routeName: 'admin-users', icon: 'pi-users' },
  { testid: 'nav-ai-assistant', label: 'AI Assistant', to: '/ai-assistant', routeName: 'ai-assistant', icon: 'pi-sparkles' },
  { testid: 'nav-profile', label: 'Profile', to: '/profile', routeName: 'profile', icon: 'pi-user' },
  { testid: 'nav-about', label: 'About', to: '/about', routeName: 'about', icon: 'pi-info-circle' },
]

const USER_NAV_ITEMS: { testid: string; label: string; to: string; routeName: string; feature: Feature | null; icon: string }[] = [
  { testid: 'nav-dashboard', label: 'Dashboard', to: '/dashboard', routeName: 'dashboard', feature: null, icon: 'pi-home' },
  { testid: 'nav-transactions', label: 'Transactions', to: '/transactions', routeName: 'transactions', feature: 'TRANSACTIONS', icon: 'pi-list' },
  { testid: 'nav-recurring', label: 'Recurring', to: '/recurring', routeName: 'recurring', feature: 'TRANSACTIONS', icon: 'pi-refresh' },
  { testid: 'nav-categories', label: 'Categories', to: '/categories', routeName: 'categories', feature: 'CATEGORIES', icon: 'pi-tags' },
  { testid: 'nav-budgets', label: 'Budgets', to: '/budgets', routeName: 'budgets', feature: 'BUDGETS', icon: 'pi-wallet' },
  { testid: 'nav-reports', label: 'Reports', to: '/reports', routeName: 'reports', feature: 'REPORTS', icon: 'pi-chart-bar' },
  { testid: 'nav-news', label: 'News', to: '/news', routeName: 'news', feature: 'NEWS', icon: 'pi-globe' },
  { testid: 'nav-ai-assistant', label: 'AI Assistant', to: '/ai-assistant', routeName: 'ai-assistant', feature: 'AI_ASSISTANT', icon: 'pi-sparkles' },
  { testid: 'nav-profile', label: 'Profile', to: '/profile', routeName: 'profile', feature: null, icon: 'pi-user' },
  { testid: 'nav-about', label: 'About', to: '/about', routeName: 'about', feature: null, icon: 'pi-info-circle' },
]

const navItems = computed(() => {
  if (authStore.isAdmin) {
    return ADMIN_NAV_ITEMS
  }
  return USER_NAV_ITEMS.filter((item) => !item.feature || entitlements.value?.[item.feature] !== false)
})

// Mobile bottom tab bar shows up to 4 fixed slots plus a "More" sheet for
// the rest — the admin nav's first 4 items (Admin Dashboard/User Management/
// AI Assistant/Profile) fill the bar, with About pushed into "More".
const PRIMARY_TAB_ROUTE_NAMES = new Set(['dashboard', 'transactions', 'budgets', 'reports'])
const ADMIN_PRIMARY_TAB_COUNT = 4

const primaryTabItems = computed(() => {
  if (authStore.isAdmin) return navItems.value.slice(0, ADMIN_PRIMARY_TAB_COUNT)
  return navItems.value.filter((item) => PRIMARY_TAB_ROUTE_NAMES.has(item.routeName))
})

const overflowNavItems = computed(() => {
  if (authStore.isAdmin) return navItems.value.slice(ADMIN_PRIMARY_TAB_COUNT)
  return navItems.value.filter((item) => !PRIMARY_TAB_ROUTE_NAMES.has(item.routeName))
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
    <!-- Desktop sidebar (full, labeled) / tablet rail (icon-only, w-[72px]) — hidden below md -->
    <aside class="hidden md:flex md:w-[72px] lg:w-64 shrink-0 bg-surface-0/70 backdrop-blur-xl border-r border-surface-200 flex-col py-4">
      <div class="flex items-center gap-2.5 px-4 pb-4 mb-2 border-b border-surface-200 md:justify-center lg:justify-start">
        <img :src="logoIcon" alt="ExpenseWise" class="w-8 h-8 object-contain shrink-0" />
        <span class="hidden lg:inline font-display text-base font-semibold tracking-tight text-surface-900">ExpenseWise</span>
      </div>
      <nav class="flex flex-col gap-1 px-2.5">
        <router-link
          v-for="item in navItems"
          :key="item.routeName"
          :data-testid="item.testid"
          :to="item.to"
          :title="item.label"
          class="group flex items-center gap-3 px-3 py-2.5 rounded-full text-sm font-medium md:justify-center lg:justify-start transition-colors duration-fast ease-out-expo"
          :class="route.name === item.routeName
            ? 'bg-primary-50 text-primary-300 shadow-glow-accent'
            : 'text-surface-500 hover:bg-surface-50 hover:text-surface-800'"
        >
          <i :class="['pi', item.icon, 'text-[15px]']" />
          <span class="hidden lg:inline">{{ item.label }}</span>
        </router-link>
      </nav>
    </aside>

    <div class="flex-1 flex flex-col min-w-0">
      <!-- Desktop/tablet header — hidden below md -->
      <header class="aurora-backdrop hidden md:flex h-16 shrink-0 items-center justify-between px-6 lg:px-8 border-b border-surface-200 bg-surface-100/80 backdrop-blur-xl">
        <span class="font-display text-lg font-semibold tracking-tight text-surface-900">{{ title }}</span>
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2.5">
            <div class="w-9 h-9 rounded-full bg-primary-50 text-primary-300 text-sm font-semibold flex items-center justify-center ring-1 ring-primary-200">
              {{ initials }}
            </div>
            <span data-testid="current-user-name" class="hidden lg:inline text-sm font-medium text-surface-800">
              {{ authStore.user?.fullName }}
            </span>
          </div>
          <button
            data-testid="logout-button"
            class="text-sm font-medium text-surface-500 hover:text-surface-900 transition-colors duration-fast ease-out-expo"
            @click="onLogout"
          >
            Log out
          </button>
        </div>
      </header>

      <!-- Mobile header — hidden from md up -->
      <header
        data-testid="mobile-header"
        class="aurora-backdrop flex md:hidden h-14 shrink-0 items-center justify-between px-4 border-b border-surface-200 bg-surface-100/80 backdrop-blur-xl"
      >
        <span class="font-display text-lg font-semibold tracking-tight text-surface-900">{{ title }}</span>
        <button
          data-testid="mobile-logout-button"
          class="w-11 h-11 rounded-full flex items-center justify-center text-surface-500 hover:text-surface-900 hover:bg-surface-50 transition-colors duration-fast ease-out-expo"
          aria-label="Log out"
          @click="onLogout"
        >
          <i class="pi pi-sign-out" />
        </button>
      </header>

      <main class="flex-1 p-4 pb-24 md:p-6 md:pb-6 lg:p-8">
        <slot />
      </main>
    </div>

    <!-- Mobile bottom tab bar — hidden from md up -->
    <nav
      data-testid="bottom-nav"
      class="md:hidden fixed bottom-0 inset-x-0 z-40 flex items-stretch h-16 bg-surface-0/85 backdrop-blur-xl border-t border-surface-200"
    >
      <router-link
        v-for="item in primaryTabItems"
        :key="item.routeName"
        :data-testid="`bottom-nav-${item.routeName}`"
        :to="item.to"
        class="flex-1 min-w-0 flex flex-col items-center justify-center gap-1 transition-colors duration-fast ease-out-expo"
        :class="route.name === item.routeName ? 'text-primary-300' : 'text-surface-500'"
      >
        <i :class="['pi', item.icon, 'text-lg']" />
        <span class="text-[10px] font-medium">{{ item.label }}</span>
      </router-link>
      <button
        v-if="overflowNavItems.length"
        data-testid="bottom-nav-more"
        type="button"
        class="flex-1 min-w-0 flex flex-col items-center justify-center gap-1 text-surface-500 transition-colors duration-fast ease-out-expo"
        @click="moreSheetOpen = true"
      >
        <i class="pi pi-ellipsis-h text-lg" />
        <span class="text-[10px] font-medium">More</span>
      </button>
    </nav>

    <!-- Mobile FAB — hidden from md up -->
    <router-link
      v-if="props.fabLabel && props.fabTo"
      :to="props.fabTo"
      data-testid="fab-button"
      :aria-label="props.fabLabel"
      class="md:hidden fixed right-4 bottom-20 z-40 w-14 h-14 rounded-full bg-primary-500 text-surface-100 flex items-center justify-center shadow-glow-accent active:scale-95 transition-transform duration-fast ease-out-expo"
    >
      <i class="pi pi-plus text-xl" />
    </router-link>
    <button
      v-else-if="props.fabLabel"
      type="button"
      data-testid="fab-button"
      :aria-label="props.fabLabel"
      class="md:hidden fixed right-4 bottom-20 z-40 w-14 h-14 rounded-full bg-primary-500 text-surface-100 flex items-center justify-center shadow-glow-accent active:scale-95 transition-transform duration-fast ease-out-expo"
      @click="emit('fab')"
    >
      <i class="pi pi-plus text-xl" />
    </button>

    <!-- Mobile "More" nav sheet -->
    <Drawer v-model:visible="moreSheetOpen" position="bottom" data-testid="more-sheet" class="md:hidden" :style="{ height: 'auto' }">
      <nav class="flex flex-col gap-1">
        <router-link
          v-for="item in overflowNavItems"
          :key="item.routeName"
          :data-testid="`more-sheet-link-${item.routeName}`"
          :to="item.to"
          class="flex items-center gap-3 px-3 py-3 rounded-lg text-sm font-medium text-surface-700 hover:bg-surface-50 hover:text-surface-900 transition-colors duration-fast ease-out-expo"
          @click="moreSheetOpen = false"
        >
          <i :class="['pi', item.icon]" />
          {{ item.label }}
        </router-link>
        <button
          data-testid="more-sheet-logout-button"
          type="button"
          class="flex items-center gap-3 px-3 py-3 rounded-lg text-sm font-medium text-danger text-left hover:bg-danger-bg transition-colors duration-fast ease-out-expo"
          @click="moreSheetOpen = false; onLogout()"
        >
          <i class="pi pi-sign-out" />
          Log out
        </button>
      </nav>
    </Drawer>
  </div>
</template>
