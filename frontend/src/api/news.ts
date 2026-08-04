import http from './http'
import type { Article } from '../types/news'

// Not paginated — the backend returns one small, shared, cached batch from
// a single upstream call, not a per-user paginated resource. See DECISIONS.md.
export async function fetchNews(): Promise<Article[]> {
  const { data } = await http.get<Article[]>('/news')
  return data
}
