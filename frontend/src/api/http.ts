import axios from 'axios'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000,
  // The refresh token travels as an httpOnly cookie — the browser must be
  // allowed to send/receive it cross-origin (Vite dev server -> API).
  withCredentials: true,
})

// Set lazily from the auth store after it's created, to avoid a circular
// import between the store (which uses `http`) and this module.
let accessTokenGetter: () => string | null = () => null
let onRefreshFailed: () => void = () => {}

export function configureAuthInterceptors(
  getAccessToken: () => string | null,
  refresh: () => Promise<string>,
  handleRefreshFailure: () => void,
) {
  accessTokenGetter = getAccessToken
  onRefreshFailed = handleRefreshFailure

  http.interceptors.request.use((config) => {
    const token = accessTokenGetter()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  http.interceptors.response.use(
    (response) => response,
    async (error: AxiosError) => {
      const originalRequest = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
      const isAuthEndpoint = originalRequest?.url?.startsWith('/auth/')

      if (error.response?.status === 401 && originalRequest && !originalRequest._retry && !isAuthEndpoint) {
        originalRequest._retry = true
        try {
          const newAccessToken = await refresh()
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
          return http(originalRequest)
        } catch {
          onRefreshFailed()
          return Promise.reject(error)
        }
      }

      return Promise.reject(error)
    },
  )
}

export default http
