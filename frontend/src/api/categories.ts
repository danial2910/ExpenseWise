import http from './http'
import type { CategoryRequest, CategoryResponse, PageResponse, PatchCategoryRequest } from '../types/category'

// Category counts per user are small (a handful of system + custom rows),
// so one page of 100 covers the whole list for this screen.
const CATEGORY_PAGE_SIZE = 100

export async function fetchCategories(): Promise<CategoryResponse[]> {
  const { data } = await http.get<PageResponse<CategoryResponse>>('/categories', {
    params: { size: CATEGORY_PAGE_SIZE },
  })
  return data.content
}

export async function createCategory(request: CategoryRequest): Promise<CategoryResponse> {
  const { data } = await http.post<CategoryResponse>('/categories', request)
  return data
}

export async function updateCategory(id: number, request: CategoryRequest): Promise<CategoryResponse> {
  const { data } = await http.put<CategoryResponse>(`/categories/${id}`, request)
  return data
}

export async function patchCategory(id: number, request: PatchCategoryRequest): Promise<CategoryResponse> {
  const { data } = await http.patch<CategoryResponse>(`/categories/${id}`, request)
  return data
}

export async function deleteCategory(id: number): Promise<void> {
  await http.delete(`/categories/${id}`)
}
