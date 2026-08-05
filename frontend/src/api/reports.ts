import http from './http'
import type { ReportFormat, ReportResponse, ReportType } from '../types/report'

interface ReportPeriodParams {
  type: ReportType
  year: number
  month?: number
}

export async function fetchReport(params: ReportPeriodParams): Promise<ReportResponse> {
  const { data } = await http.get<ReportResponse>('/reports', { params })
  return data
}

// content-disposition's filename is quoted (RFC 6266) — strip the quotes so
// the browser download doesn't literally include them in the saved name.
function filenameFromContentDisposition(contentDisposition: string | undefined, fallback: string): string {
  const match = contentDisposition?.match(/filename="?([^";]+)"?/)
  return match ? match[1] : fallback
}

// Downloads go through the shared axios instance with responseType: 'blob'
// (never a bare fetch, per CLAUDE.md), then trigger the browser's native
// save dialog via a temporary, invisible <a download> — the standard way to
// turn an in-memory blob response into a file download.
export async function downloadReport(params: ReportPeriodParams & { format: ReportFormat }): Promise<void> {
  const response = await http.get('/reports/download', { params, responseType: 'blob' })
  const filename = filenameFromContentDisposition(
    response.headers['content-disposition'],
    `expensewise-report.${params.format === 'pdf' ? 'pdf' : 'xlsx'}`,
  )

  const url = URL.createObjectURL(response.data as Blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

// Print support: open the generated PDF in a new tab so the browser's own
// print dialog (Ctrl/Cmd+P against the PDF viewer) handles it — reuses the
// same download endpoint's bytes rather than a second print-only code path.
export async function openReportPdfForPrint(params: ReportPeriodParams): Promise<void> {
  const response = await http.get('/reports/download', { params: { ...params, format: 'pdf' }, responseType: 'blob' })
  const url = URL.createObjectURL(response.data as Blob)
  window.open(url, '_blank')
}
