import type { BudgetMonthResponse } from './budget'
import type { TransactionResponse } from './transaction'

export interface DashboardSummary {
  thisMonthIncome: number | string
  thisMonthExpense: number | string
  thisMonthBalance: number | string
  overallBalance: number | string
}

export interface MonthlyFlowPoint {
  // First day of the month, e.g. "2026-07-01".
  month: string
  income: number | string
  expense: number | string
}

export interface CategoryAmount {
  categoryId: number
  categoryName: string
  categoryIcon: string | null
  amount: number | string
}

// budgetUtilisation/recentTransactions reuse the budget and transaction
// modules' own response shapes directly — the dashboard never recomputes
// budget progress or re-shapes a transaction row.
export interface DashboardResponse {
  summary: DashboardSummary
  monthlyTrend: MonthlyFlowPoint[]
  expenseByCategory: CategoryAmount[]
  budgetUtilisation: BudgetMonthResponse
  recentTransactions: TransactionResponse[]
}
