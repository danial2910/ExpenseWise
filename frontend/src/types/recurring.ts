import type { TransactionType } from './transaction'

export type Frequency = 'WEEKLY' | 'MONTHLY' | 'YEARLY'

export interface RecurringRuleResponse {
  id: number
  type: TransactionType
  // BigDecimal is serialized by Jackson as a plain JSON number, but is
  // handled as a string too defensively (see MoneyDisplay.vue).
  amount: number | string
  categoryId: number
  categoryName: string
  categoryIcon: string | null
  description: string | null
  frequency: Frequency
  startDate: string
  endDate: string | null
  nextDueDate: string
  isActive: boolean
}

export interface RecurringRuleRequest {
  type: TransactionType
  amount: number | string
  categoryId: number
  description?: string | null
  frequency: Frequency
  startDate: string
  endDate?: string | null
}

export interface PatchRecurringRuleRequest {
  type?: TransactionType
  amount?: number | string
  categoryId?: number
  description?: string | null
  frequency?: Frequency
  startDate?: string
  endDate?: string | null
  isActive?: boolean
}

export interface GenerateDueResponse {
  transactionsGenerated: number
}

export interface PageResponse<T> {
  content: T[]
}
