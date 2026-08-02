export type Gender = 'FEMALE' | 'MALE' | 'NON_BINARY' | 'SELF_DESCRIBED' | 'NOT_SPECIFIED'

export interface UserResponse {
  id: number
  email: string
  fullName: string
  role: 'USER' | 'ADMIN'
  active: boolean
  createdAt: string
  phone: string | null
  dateOfBirth: string | null
  gender: Gender | null
  address: string | null
  avatarUrl: string | null
}

export interface LoginResponse {
  accessToken: string
  user: UserResponse
}

export interface RefreshResponse {
  accessToken: string
}

export interface ApiErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors?: Record<string, string>
}
