import http from './http'
import type { UserResponse } from '../types/auth'
import type { PageResponse } from '../types/transaction'
import type {
  AdminCreateUserRequest,
  AdminDashboardResponse,
  AdminUpdateUserAccessRequest,
  AdminUserDetailResponse,
  AdminUserListParams,
} from '../types/admin'

export async function fetchAdminUsers(params: AdminUserListParams): Promise<PageResponse<UserResponse>> {
  const { data } = await http.get<PageResponse<UserResponse>>('/admin/users', { params })
  return data
}

export async function fetchAdminUser(id: number): Promise<AdminUserDetailResponse> {
  const { data } = await http.get<AdminUserDetailResponse>(`/admin/users/${id}`)
  return data
}

export async function createAdminUser(request: AdminCreateUserRequest): Promise<AdminUserDetailResponse> {
  const { data } = await http.post<AdminUserDetailResponse>('/admin/users', request)
  return data
}

export async function updateAdminUserAccess(
  id: number,
  request: AdminUpdateUserAccessRequest,
): Promise<AdminUserDetailResponse> {
  const { data } = await http.put<AdminUserDetailResponse>(`/admin/users/${id}/access`, request)
  return data
}

export async function updateAdminUserStatus(id: number, active: boolean): Promise<UserResponse> {
  const { data } = await http.patch<UserResponse>(`/admin/users/${id}/status`, { active })
  return data
}

export async function deleteAdminUser(id: number): Promise<void> {
  await http.delete(`/admin/users/${id}`)
}

export async function fetchAdminDashboard(months = 6, recentLimit = 5): Promise<AdminDashboardResponse> {
  const { data } = await http.get<AdminDashboardResponse>('/admin/dashboard', { params: { months, recentLimit } })
  return data
}
