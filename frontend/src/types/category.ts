export type CategoryType = 'INCOME' | 'EXPENSE'

export interface CategoryResponse {
  id: number
  name: string
  type: CategoryType
  icon: string | null
  system: boolean
}

export interface CategoryRequest {
  name: string
  type: CategoryType
  icon: string
}

export interface PatchCategoryRequest {
  name?: string
  type?: CategoryType
  icon?: string
}

export interface PageResponse<T> {
  content: T[]
}
