import { apiRequest } from '../api/client'

export interface AdminUserItem {
  id: string
  username: string
  displayName: string
  email: string | null
  role: string
  status: number
  createdAt: string | null
}

export interface AdminUserListParams {
  keyword?: string
  page?: number
  pageSize?: number
}

export function getAdminUsers(params: AdminUserListParams): Promise<{ total: number; list: AdminUserItem[] }> {
  const query = new URLSearchParams()
  if (params.keyword) query.set('keyword', params.keyword)
  if (params.page) query.set('page', String(params.page))
  if (params.pageSize) query.set('pageSize', String(params.pageSize))
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return apiRequest<{ total: number; list: AdminUserItem[] }>(`/api/admin/users${suffix}`, { method: 'GET' }, true)
}

export function deactivateUser(userId: string): Promise<void> {
  return apiRequest<void>(`/api/admin/users/${userId}`, { method: 'DELETE' }, true)
}
