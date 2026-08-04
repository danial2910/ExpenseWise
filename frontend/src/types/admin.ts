import type { UserResponse } from './auth'

export type Feature = 'TRANSACTIONS' | 'CATEGORIES' | 'BUDGETS' | 'REPORTS' | 'AI_ASSISTANT' | 'NEWS'

export const ALL_FEATURES: Feature[] = ['TRANSACTIONS', 'CATEGORIES', 'BUDGETS', 'REPORTS', 'AI_ASSISTANT', 'NEWS']

export const FEATURE_LABELS: Record<Feature, string> = {
  TRANSACTIONS: 'Transactions',
  CATEGORIES: 'Categories',
  BUDGETS: 'Budgets',
  REPORTS: 'Reports',
  AI_ASSISTANT: 'AI Assistant',
  NEWS: 'News',
}

export interface AdminUserDetailResponse {
  user: UserResponse
  enabledFeatures: Record<Feature, boolean>
}

export interface AdminUserListParams {
  search?: string
  role?: 'USER' | 'ADMIN'
  active?: boolean
  page?: number
  size?: number
}

export interface AdminCreateUserRequest {
  fullName: string
  email: string
  role: 'USER' | 'ADMIN'
  enabledFeatures?: Feature[] | null
}

export interface AdminUpdateUserAccessRequest {
  role: 'USER' | 'ADMIN'
  active: boolean
  enabledFeatures: Feature[]
}

export interface AdminDashboardSummary {
  totalUsers: number
  activeUsers: number
  disabledUsers: number
  newUsersThisMonth: number
  adminUsers: number
  regularUsers: number
  totalTransactions: number
  totalBudgets: number
}

export interface MonthlyCountPoint {
  // First day of the month, e.g. "2026-07-01".
  month: string
  count: number
}

export interface FeatureUsage {
  feature: Feature
  enabledCount: number
  percentage: number
}

export interface RecentSignup {
  id: number
  fullName: string
  email: string
  createdAt: string
  active: boolean
}

export interface AdminDashboardResponse {
  summary: AdminDashboardSummary
  signupsOverTime: MonthlyCountPoint[]
  activityOverTime: MonthlyCountPoint[]
  featureUsage: FeatureUsage[]
  recentSignups: RecentSignup[]
}
