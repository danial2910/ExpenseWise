import http from './http'
import type { UserResponse } from '../types/auth'
import type { ChangePasswordRequest, LoginHistoryEntry, UpdateProfileRequest } from '../types/user'
import type { PageResponse } from '../types/transaction'

export async function fetchCurrentUser(): Promise<UserResponse> {
  const { data } = await http.get<UserResponse>('/users/me')
  return data
}

export async function updateProfile(request: UpdateProfileRequest): Promise<UserResponse> {
  const { data } = await http.patch<UserResponse>('/users/me', request)
  return data
}

export async function changePassword(request: ChangePasswordRequest): Promise<void> {
  await http.patch('/users/me/password', request)
}

export async function uploadAvatar(file: File): Promise<UserResponse> {
  const formData = new FormData()
  formData.append('file', file)
  const { data } = await http.post<UserResponse>('/users/me/avatar', formData)
  return data
}

export async function removeAvatar(): Promise<UserResponse> {
  const { data } = await http.delete<UserResponse>('/users/me/avatar')
  return data
}

export async function fetchLoginHistory(page = 0, size = 5): Promise<PageResponse<LoginHistoryEntry>> {
  const { data } = await http.get<PageResponse<LoginHistoryEntry>>('/users/me/login-history', {
    params: { page, size },
  })
  return data
}

export async function logoutOtherSessions(): Promise<void> {
  await http.post('/auth/logout-others')
}
