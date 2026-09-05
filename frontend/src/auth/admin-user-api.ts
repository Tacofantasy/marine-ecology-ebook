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
  status?: number
  role?: 'ADMIN' | 'USER'
  page?: number
  pageSize?: number
}

export interface CreateSubAdministratorRequest {
  username: string
  displayName: string
  email?: string
  password: string
}

export function getAdminUsers(params: AdminUserListParams): Promise<{ total: number; list: AdminUserItem[] }> {
  const query = new URLSearchParams()
  if (params.keyword) query.set('keyword', params.keyword)
  if (params.status !== undefined) query.set('status', String(params.status))
  if (params.role) query.set('role', params.role)
  if (params.page) query.set('page', String(params.page))
  if (params.pageSize) query.set('pageSize', String(params.pageSize))
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return apiRequest<{ total: number; list: AdminUserItem[] }>(`/api/admin/users${suffix}`, { method: 'GET' }, true)
}

export function deactivateUser(userId: string): Promise<void> {
  return apiRequest<void>(`/api/admin/users/${userId}`, { method: 'DELETE' }, true)
}

export function createSubAdministrator(payload: CreateSubAdministratorRequest): Promise<AdminUserItem> {
  return apiRequest<AdminUserItem>('/api/admin/users/administrators', {
    method: 'POST',
    body: JSON.stringify(payload),
  }, true)
}

export function updateUserStatus(userId: string, status: 0 | 1): Promise<void> {
  return apiRequest<void>(`/api/admin/users/${userId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  }, true)
}

export function resetUserPassword(userId: string, password: string): Promise<void> {
  return apiRequest<void>(`/api/admin/users/${userId}/password`, {
    method: 'PUT',
    body: JSON.stringify({ password }),
  }, true)
}
