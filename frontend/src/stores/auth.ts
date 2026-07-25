import { defineStore } from 'pinia'
import http, { configureAuthInterceptors } from '../api/http'
import type { LoginResponse, RefreshResponse, UserResponse } from '../types/auth'

interface AuthState {
  user: UserResponse | null
  // Access token is kept in memory only — never localStorage — to limit
  // XSS exposure. It's lost on a hard page reload, which is why
  // bootstrap() silently re-derives it from the httpOnly refresh cookie.
  accessToken: string | null
  bootstrapped: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    user: null,
    accessToken: null,
    bootstrapped: false,
  }),

  getters: {
    isAuthenticated: (state) => state.user !== null,
    isAdmin: (state) => state.user?.role === 'ADMIN',
  },

  actions: {
    async register(fullName: string, email: string, password: string) {
      const { data } = await http.post<LoginResponse>('/auth/register', { fullName, email, password })
      this.applyLoginResult(data)
    },

    async login(email: string, password: string) {
      const { data } = await http.post<LoginResponse>('/auth/login', { email, password })
      this.applyLoginResult(data)
    },

    async logout() {
      try {
        await http.post('/auth/logout')
      } finally {
        this.clear()
      }
    },

    async refreshAccessToken(): Promise<string> {
      const { data } = await http.post<RefreshResponse>('/auth/refresh')
      this.accessToken = data.accessToken
      return data.accessToken
    },

    /**
     * Called once on app startup. There is no access token in memory yet
     * after a hard reload, but the httpOnly refresh cookie may still be
     * valid — silently try to trade it for a fresh access token and the
     * current user before the router makes any auth-guard decisions.
     */
    async bootstrap() {
      if (this.bootstrapped) {
        return
      }
      try {
        await this.refreshAccessToken()
        const { data } = await http.get<UserResponse>('/users/me')
        this.user = data
      } catch {
        this.clear()
      } finally {
        this.bootstrapped = true
      }
    },

    applyLoginResult(data: LoginResponse) {
      this.accessToken = data.accessToken
      this.user = data.user
      this.bootstrapped = true
    },

    clear() {
      this.user = null
      this.accessToken = null
    },
  },
})

export function installAuthInterceptors() {
  const authStore = useAuthStore()
  configureAuthInterceptors(
    () => authStore.accessToken,
    () => authStore.refreshAccessToken(),
    () => authStore.clear(),
  )
}
