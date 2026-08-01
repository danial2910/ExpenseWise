export type AiMessageRole = 'user' | 'assistant'

export interface AiMessageResponse {
  id: number
  role: AiMessageRole
  content: string
  createdAt: string
}

export interface AiConversationSummaryResponse {
  id: number
  title: string
  createdAt: string
}

export interface AiConversationResponse {
  id: number
  title: string
  createdAt: string
  messages: AiMessageResponse[]
}

export interface CreateConversationRequest {
  firstMessage?: string | null
}

export interface PostMessageRequest {
  content: string
}

// severity drives color only — the body text is plain prose from the
// backend (already-formatted sentences, not a raw numeric field), so it
// renders as-is rather than through MoneyDisplay.
export type InsightSeverity = 'CRITICAL' | 'WARNING' | 'POSITIVE'

export interface InsightResponse {
  title: string
  body: string
  severity: InsightSeverity
}

export interface InsightsResponse {
  insights: InsightResponse[]
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
