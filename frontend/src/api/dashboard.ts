import http from './http'
import type { DashboardResponse } from '../types/dashboard'

export async function fetchDashboard(months = 6, recentLimit = 5): Promise<DashboardResponse> {
  const { data } = await http.get<DashboardResponse>('/dashboard', { params: { months, recentLimit } })
  return data
}
