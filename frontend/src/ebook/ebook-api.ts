import { apiRequest } from '../api/client'

export type EbookStatus = 'DRAFT' | 'PUBLISHED'

export interface EbookItem {
  id: string
  categoryId: string
  categoryName: string
  title: string
  coverUrl: string | null
  summary: string | null
  sourceNote: string | null
  status: EbookStatus
  publishedAt: string | null
  updatedAt: string | null
  likeCount?: string
}

export interface PageData<T> {
  total: number
  list: T[]
}

export interface EbookPayload {
  categoryId: string
  title: string
  summary?: string
  sourceNote?: string
}

interface EbookQuery {
  categoryId?: string | null
  keyword?: string
  page?: number
  pageSize?: number
}

function queryString(query: EbookQuery) {
  const params = new URLSearchParams()
  if (query.categoryId) params.set('categoryId', String(query.categoryId))
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
  params.set('page', String(query.page ?? 1))
  params.set('pageSize', String(query.pageSize ?? 10))
  return params.toString()
}

export function getPublicEbooks(query: EbookQuery) {
  return apiRequest<PageData<EbookItem>>(`/api/ebooks?${queryString(query)}`)
}

export function getPublicEbook(ebookId: string) {
  return apiRequest<EbookItem>(`/api/ebooks/${ebookId}`)
}

export function getAdminEbooks(query: EbookQuery) {
  return apiRequest<PageData<EbookItem>>(`/api/admin/ebooks?${queryString(query)}`, {}, true)
}

export function getAdminEbook(ebookId: string) {
  return apiRequest<EbookItem>(`/api/admin/ebooks/${ebookId}`, {}, true)
}

export function createEbook(payload: EbookPayload) {
  return apiRequest<EbookItem>('/api/admin/ebooks', { method: 'POST', body: JSON.stringify(payload) }, true)
}

export function updateEbook(ebookId: string, payload: EbookPayload) {
  return apiRequest<EbookItem>(`/api/admin/ebooks/${ebookId}`, { method: 'PUT', body: JSON.stringify(payload) }, true)
}

export function uploadEbookCover(ebookId: string, file: File) {
  const body = new FormData()
  body.append('file', file)
  return apiRequest<string>(`/api/admin/ebooks/cover?ebookId=${ebookId}`, { method: 'POST', body }, true)
}

export function publishEbook(ebookId: string) {
  return apiRequest<EbookItem>(`/api/admin/ebooks/${ebookId}/publish`, { method: 'POST' }, true)
}

export function unpublishEbook(ebookId: string) {
  return apiRequest<EbookItem>(`/api/admin/ebooks/${ebookId}/unpublish`, { method: 'POST' }, true)
}

export function deleteEbook(ebookId: string) {
  return apiRequest<void>(`/api/admin/ebooks/${ebookId}`, { method: 'DELETE' }, true)
}
