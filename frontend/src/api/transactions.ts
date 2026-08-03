import http from './http'
import type {
  PageResponse,
  PatchTransactionRequest,
  TransactionListParams,
  TransactionRequest,
  TransactionResponse,
  TransactionSummaryParams,
  TransactionSummaryResponse,
} from '../types/transaction'

export async function fetchTransactions(params: TransactionListParams): Promise<PageResponse<TransactionResponse>> {
  const { data } = await http.get<PageResponse<TransactionResponse>>('/transactions', { params })
  return data
}

export async function fetchSummary(params: TransactionSummaryParams): Promise<TransactionSummaryResponse> {
  const { data } = await http.get<TransactionSummaryResponse>('/transactions/summary', { params })
  return data
}

export async function createTransaction(request: TransactionRequest): Promise<TransactionResponse> {
  const { data } = await http.post<TransactionResponse>('/transactions', request)
  return data
}

export async function updateTransaction(id: number, request: TransactionRequest): Promise<TransactionResponse> {
  const { data } = await http.put<TransactionResponse>(`/transactions/${id}`, request)
  return data
}

export async function patchTransaction(id: number, request: PatchTransactionRequest): Promise<TransactionResponse> {
  const { data } = await http.patch<TransactionResponse>(`/transactions/${id}`, request)
  return data
}

export async function deleteTransaction(id: number): Promise<void> {
  await http.delete(`/transactions/${id}`)
}

export async function uploadReceipt(transactionId: number, file: File): Promise<TransactionResponse> {
  const formData = new FormData()
  formData.append('file', file)
  const { data } = await http.post<TransactionResponse>(`/transactions/${transactionId}/receipt`, formData)
  return data
}

export async function removeReceipt(transactionId: number): Promise<TransactionResponse> {
  const { data } = await http.delete<TransactionResponse>(`/transactions/${transactionId}/receipt`)
  return data
}
