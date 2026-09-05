import { apiRequest } from '../api/client'

export interface ChapterItem {
  id: string
  ebookId: string
  title: string
  sortOrder: number
  status: 'DRAFT' | 'PUBLISHED'
  sourceNote: string | null
  viewCount: string
  updatedAt: string | null
}

export interface ChapterDetail extends ChapterItem {
  content: string
}

export interface ChapterPayload {
  title: string
  content: string
  sourceNote?: string
}

export function getAdminChapters(ebookId: string) {
  return apiRequest<ChapterItem[]>(`/api/admin/ebooks/${ebookId}/chapters`, {}, true)
}

export function getAdminChapter(ebookId: string, chapterId: string) {
  return apiRequest<ChapterDetail>(`/api/admin/ebooks/${ebookId}/chapters/${chapterId}`, {}, true)
}

export function createChapter(ebookId: string, payload: ChapterPayload) {
  return apiRequest<ChapterItem>(`/api/admin/ebooks/${ebookId}/chapters`, {
    method: 'POST', body: JSON.stringify(payload),
  }, true)
}

export function updateChapter(ebookId: string, chapterId: string, payload: ChapterPayload) {
  return apiRequest<ChapterItem>(`/api/admin/ebooks/${ebookId}/chapters/${chapterId}`, {
    method: 'PUT', body: JSON.stringify(payload),
  }, true)
}

export function deleteChapter(ebookId: string, chapterId: string) {
  return apiRequest<void>(`/api/admin/ebooks/${ebookId}/chapters/${chapterId}`, { method: 'DELETE' }, true)
}

export function reorderChapters(ebookId: string, chapterIds: string[]) {
  return apiRequest<ChapterItem[]>(`/api/admin/ebooks/${ebookId}/chapters/order`, {
    method: 'PUT', body: JSON.stringify({ chapterIds }),
  }, true)
}

export function uploadContentImage(file: File) {
  const body = new FormData()
  body.append('file', file)
  return apiRequest<string>('/api/admin/content-images', { method: 'POST', body }, true)
}

export function getPublicChapters(ebookId: string) {
  return apiRequest<ChapterItem[]>(`/api/ebooks/${ebookId}/chapters`)
}

export function getPublicChapter(ebookId: string, chapterId: string) {
  return apiRequest<ChapterDetail>(`/api/ebooks/${ebookId}/chapters/${chapterId}`)
}

export function recordChapterRead(ebookId: string, chapterId: string) {
  return apiRequest<void>(`/api/ebooks/${ebookId}/chapters/${chapterId}/read`, { method: 'POST' }, true)
}
