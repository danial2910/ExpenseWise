import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/health', name: 'health', component: HomeView },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/auth/LoginView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/auth/RegisterView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: () => import('../views/auth/ForgotPasswordView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/reset-password',
      name: 'reset-password',
      component: () => import('../views/auth/ResetPasswordView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/check-email',
      name: 'check-email',
      component: () => import('../views/auth/CheckEmailView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('../views/DashboardView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/categories',
      name: 'categories',
      component: () => import('../views/CategoriesView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/transactions',
      name: 'transactions',
      component: () => import('../views/TransactionsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/budgets',
      name: 'budgets',
      component: () => import('../views/BudgetsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/ai-assistant',
      name: 'ai-assistant',
      component: () => import('../views/AiAssistantView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('../views/ProfileView.vue'),
      meta: { requiresAuth: true },
    },
    { path: '/', redirect: '/dashboard' },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  // Vue Router resolves its initial navigation (and so runs this guard)
  // as soon as the router is installed, independent of when the app
  // mounts — it does not wait for main.ts's own bootstrap() call. On a
  // hard reload, awaiting it here (a no-op once already bootstrapped —
  // see AuthState.bootstrapped) is what stops the guard from redirecting
  // a still-valid session to /login before the refresh cookie is checked.
  if (!authStore.bootstrapped) {
    await authStore.bootstrap()
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAdmin && !authStore.isAdmin) {
    return { name: 'dashboard' }
  }

  if (to.meta.guestOnly && authStore.isAuthenticated) {
    return { name: 'dashboard' }
  }

  return true
})

export default router
