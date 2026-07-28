import http from './http'
import type { BudgetMonthResponse, BudgetRequest, BudgetResponse } from '../types/budget'

export async function fetchMonthBudgets(month?: string): Promise<BudgetMonthResponse> {
  const { data } = await http.get<BudgetMonthResponse>('/budgets', { params: { month } })
  return data
}

export async function createBudget(request: BudgetRequest): Promise<BudgetResponse> {
  const { data } = await http.post<BudgetResponse>('/budgets', request)
  return data
}

export async function updateBudget(id: number, request: BudgetRequest): Promise<BudgetResponse> {
  const { data } = await http.put<BudgetResponse>(`/budgets/${id}`, request)
  return data
}

export async function deleteBudget(id: number): Promise<void> {
  await http.delete(`/budgets/${id}`)
}
