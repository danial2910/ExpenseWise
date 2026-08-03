import type { CategoryType } from './category'

// Transactions share the same INCOME/EXPENSE type as categories.
export type TransactionType = CategoryType

export interface TransactionResponse {
  id: number
  type: TransactionType
  // BigDecimal is serialized by Jackson as a plain JSON number, but is
  // handled as a string too defensively (see MoneyDisplay.vue).
  amount: number | string
  categoryId: number
  categoryName: string
  categoryIcon: string | null
  transactionDate: string
  description: string | null
  createdAt: string
  updatedAt: string
  // A freshly signed URL when a receipt is attached, regenerated on every
  // fetch (signed URLs expire) — never cached client-side.
  receiptUrl: string | null
}

export interface TransactionRequest {
  type: TransactionType
  amount: number | string
  categoryId: number
  transactionDate: string
  description?: string | null
}

export interface PatchTransactionRequest {
  type?: TransactionType
  amount?: number | string
  categoryId?: number
  transactionDate?: string
  description?: string | null
}

export interface TransactionSummaryResponse {
  totalIncome: number | string
  totalExpense: number | string
  balance: number | string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface TransactionListParams {
  type?: TransactionType
  categoryId?: number
  from?: string
  to?: string
  search?: string
  page?: number
  size?: number
  sort?: string
}

export interface TransactionSummaryParams {
  type?: TransactionType
  categoryId?: number
  from?: string
  to?: string
}
