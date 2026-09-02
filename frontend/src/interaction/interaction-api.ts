import { apiRequest } from '../api/client'
import type { EbookStatus, PageData } from '../ebook/ebook-api'

export interface InteractionState {
  liked: boolean
  favorited: boolean
  likeCount: string
}

export interface FavoriteEbookItem {
  id: string
  categoryId: string
  categoryName: string
  title: string
  coverUrl: string | null
  summary: string | null
  status: EbookStatus
  publishedAt: string | null
  updatedAt: string | null
  likeCount: string
  favoritedAt: string
}

export function getInteractionState(ebookId: string) {
  return apiRequest<InteractionState>(`/api/ebooks/${ebookId}/interaction`, {}, true)
}

export function likeEbook(ebookId: string) {
  return apiRequest<InteractionState>(`/api/ebooks/${ebookId}/like`, { method: 'POST' }, true)
}

export function unlikeEbook(ebookId: string) {
  return apiRequest<InteractionState>(`/api/ebooks/${ebookId}/like`, { method: 'DELETE' }, true)
}

export function favoriteEbook(ebookId: string) {
  return apiRequest<InteractionState>(`/api/ebooks/${ebookId}/favorite`, { method: 'POST' }, true)
}

export function unfavoriteEbook(ebookId: string) {
  return apiRequest<InteractionState>(`/api/ebooks/${ebookId}/favorite`, { method: 'DELETE' }, true)
}

export function getFavorites(page = 1, pageSize = 10) {
  return apiRequest<PageData<FavoriteEbookItem>>(`/api/me/favorites?page=${page}&pageSize=${pageSize}`, {}, true)
}
