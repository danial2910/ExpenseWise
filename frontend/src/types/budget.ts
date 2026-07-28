// BigDecimal fields are serialized by Jackson as plain JSON numbers, but
// are handled as strings too defensively (see MoneyDisplay.vue). Fields
// that only exist when a budget limit is set are null otherwise — never 0
// — since 0 would misrepresent "no limit" as "a limit of RM 0".

export interface OverallBudgetLine {
  budgetId: number | null
  amount: number | string | null
  spent: number | string
  remaining: number | string | null
  progressPercent: number | string | null
  exceeded: boolean
}

export interface CategoryBudgetLine {
  categoryId: number
  categoryName: string
  categoryIcon: string | null
  budgetId: number | null
  amount: number | string | null
  spent: number | string
  remaining: number | string | null
  progressPercent: number | string | null
  exceeded: boolean
}

export interface BudgetMonthResponse {
  periodMonth: string
  overall: OverallBudgetLine
  categories: CategoryBudgetLine[]
}

export interface BudgetResponse {
  id: number
  categoryId: number | null
  categoryName: string | null
  amount: number | string
  periodMonth: string
  spent: number | string
  remaining: number | string
  progressPercent: number | string
  exceeded: boolean
  createdAt: string
}

export interface BudgetRequest {
  amount: number | string
  categoryId?: number | null
  periodMonth: string
}
