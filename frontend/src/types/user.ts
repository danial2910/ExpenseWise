export interface UpdateProfileRequest {
  fullName: string
  phone?: string
  dateOfBirth?: string | null
  gender?: string
  address?: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export interface LoginHistoryEntry {
  occurredAt: string
  ipAddress: string | null
  status: 'Success' | 'Failed'
}
