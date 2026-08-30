import { apiRequest } from '../api/client'
import type { UserProfile } from './session'

interface LoginResponse {
  token: string
  user: UserProfile
}

export function register(payload: { username: string; email: string; password: string }) {
  return apiRequest<UserProfile>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function login(payload: { account: string; password: string }) {
  return apiRequest<LoginResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getCurrentUser() {
  return apiRequest<UserProfile>('/api/auth/me', {}, true)
}
