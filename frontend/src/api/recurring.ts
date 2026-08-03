import http from './http'
import type {
  GenerateDueResponse,
  PageResponse,
  PatchRecurringRuleRequest,
  RecurringRuleRequest,
  RecurringRuleResponse,
} from '../types/recurring'

// Recurring rules are a small, non-paginated screen per the design (no
// pagination controls) — one page of 100 (the API's max page size) covers
// the whole list, same precedent as api/categories.ts.
const RECURRING_PAGE_SIZE = 100

export async function fetchRecurringRules(): Promise<RecurringRuleResponse[]> {
  const { data } = await http.get<PageResponse<RecurringRuleResponse>>('/recurring', {
    params: { size: RECURRING_PAGE_SIZE },
  })
  return data.content
}

export async function createRecurringRule(request: RecurringRuleRequest): Promise<RecurringRuleResponse> {
  const { data } = await http.post<RecurringRuleResponse>('/recurring', request)
  return data
}

export async function updateRecurringRule(id: number, request: RecurringRuleRequest): Promise<RecurringRuleResponse> {
  const { data } = await http.put<RecurringRuleResponse>(`/recurring/${id}`, request)
  return data
}

export async function patchRecurringRule(
  id: number,
  request: PatchRecurringRuleRequest,
): Promise<RecurringRuleResponse> {
  const { data } = await http.patch<RecurringRuleResponse>(`/recurring/${id}`, request)
  return data
}

export async function deleteRecurringRule(id: number): Promise<void> {
  await http.delete(`/recurring/${id}`)
}

export async function generateDueNow(): Promise<GenerateDueResponse> {
  const { data } = await http.post<GenerateDueResponse>('/recurring/generate-due')
  return data
}
