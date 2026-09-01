import { apiRequest } from '../api/client'

export interface CategoryTreeItem {
  id: number
  parentId: number | null
  name: string
  sortOrder: number
  children: CategoryTreeItem[]
}

export function getCategories() {
  return apiRequest<CategoryTreeItem[]>('/api/categories')
}

export function getAdminCategories() {
  return apiRequest<CategoryTreeItem[]>('/api/admin/categories', {}, true)
}

export function createCategory(payload: { name: string; parentId?: number }) {
  return apiRequest<CategoryTreeItem>('/api/admin/categories', {
    method: 'POST',
    body: JSON.stringify(payload),
  }, true)
}

export function updateCategory(categoryId: number, payload: { name: string }) {
  return apiRequest<CategoryTreeItem>(`/api/admin/categories/${categoryId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  }, true)
}

export function deleteCategory(categoryId: number) {
  return apiRequest<void>(`/api/admin/categories/${categoryId}`, { method: 'DELETE' }, true)
}
