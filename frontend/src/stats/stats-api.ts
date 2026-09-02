import { apiRequest } from '../api/client'

export interface StatsSummary {
  totalViewCount: number
  totalLikeCount: number
  todayViewCount: number
  todayLikeCount: number
  publishedEbookCount: number
  activeUserCount: number
  totalWordCount: number
  estimatedReadingMinutes: number
}

export interface TrendPoint {
  date: string
  viewDelta: number
  likeDelta: number
}

export function getStatsSummary(): Promise<StatsSummary> {
  return apiRequest<StatsSummary>('/api/stats/summary', { method: 'GET' })
}

export function getStatsTrend(days = 30): Promise<TrendPoint[]> {
  return apiRequest<TrendPoint[]>(`/api/stats/trend?days=${days}`, { method: 'GET' })
}
