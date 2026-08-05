import type { TransactionResponse } from './transaction'

export type ReportType = 'MONTHLY' | 'YEARLY'
export type ReportFormat = 'pdf' | 'excel'

export interface CategoryBreakdownLine {
  categoryId: number
  categoryName: string
  categoryIcon: string | null
  amount: number | string
  percentage: number | string
}

// totalBudgeted/totalRemaining are null when hasBudget is false — no overall
// budget was ever set for the period, matching the budget module's "null,
// not zero" convention for an unset limit.
export interface BudgetSummary {
  totalBudgeted: number | string | null
  totalSpent: number | string
  totalRemaining: number | string | null
  hasBudget: boolean
}

// One point per calendar month (first day of month, e.g. "2026-03-01") —
// reuses the same {month, income, expense} shape as the Dashboard module's
// own trend series. Screen-only: neither exporter renders it.
export interface MonthlyFlowPoint {
  month: string
  income: number | string
  expense: number | string
}

export interface ReportResponse {
  type: ReportType
  periodStart: string
  periodEnd: string
  totalIncome: number | string
  totalExpense: number | string
  netBalance: number | string
  categoryBreakdown: CategoryBreakdownLine[]
  transactions: TransactionResponse[]
  budgetSummary: BudgetSummary
  monthlyTrend: MonthlyFlowPoint[]
}
