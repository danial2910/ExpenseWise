import http from './http'
import type {
  AiConversationResponse,
  AiConversationSummaryResponse,
  AiMessageResponse,
  CreateConversationRequest,
  InsightsResponse,
  PageResponse,
  PostMessageRequest,
} from '../types/ai'

const CONVERSATION_LIST_SIZE = 50

export async function fetchConversations(): Promise<PageResponse<AiConversationSummaryResponse>> {
  const { data } = await http.get<PageResponse<AiConversationSummaryResponse>>('/ai/conversations', {
    params: { size: CONVERSATION_LIST_SIZE },
  })
  return data
}

export async function fetchConversation(id: number): Promise<AiConversationResponse> {
  const { data } = await http.get<AiConversationResponse>(`/ai/conversations/${id}`)
  return data
}

export async function createConversation(request: CreateConversationRequest): Promise<AiConversationResponse> {
  const { data } = await http.post<AiConversationResponse>('/ai/conversations', request)
  return data
}

export async function postMessage(conversationId: number, request: PostMessageRequest): Promise<AiMessageResponse> {
  const { data } = await http.post<AiMessageResponse>(`/ai/conversations/${conversationId}/messages`, request)
  return data
}

export async function deleteConversation(id: number): Promise<void> {
  await http.delete(`/ai/conversations/${id}`)
}

export async function fetchInsights(): Promise<InsightsResponse> {
  const { data } = await http.get<InsightsResponse>('/ai/insights')
  return data
}
