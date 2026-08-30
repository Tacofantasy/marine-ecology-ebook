import { reactive } from 'vue'

export type UserRole = 'ADMIN' | 'USER'

export interface UserProfile {
  id: number
  username: string
  email: string | null
  role: UserRole
  status: number
}

interface StoredSession {
  token: string
  user: UserProfile
}

const storageKey = 'marine-ebook-auth'

function readStoredSession(): StoredSession | null {
  try {
    const raw = localStorage.getItem(storageKey)
    if (!raw) {
      return null
    }
    const stored = JSON.parse(raw) as Partial<StoredSession>
    if (!stored.token || !stored.user) {
      localStorage.removeItem(storageKey)
      return null
    }
    return stored as StoredSession
  } catch {
    localStorage.removeItem(storageKey)
    return null
  }
}

const stored = readStoredSession()

export const authState = reactive<{ token: string | null; user: UserProfile | null }>({
  token: stored?.token ?? null,
  user: stored?.user ?? null,
})

export function getToken() {
  return authState.token
}

export function setSession(token: string, user: UserProfile) {
  authState.token = token
  authState.user = user
  localStorage.setItem(storageKey, JSON.stringify({ token, user }))
}

export function clearSession() {
  authState.token = null
  authState.user = null
  localStorage.removeItem(storageKey)
}

export function refreshUser(user: UserProfile) {
  if (!authState.token) {
    return
  }
  authState.user = user
  localStorage.setItem(storageKey, JSON.stringify({ token: authState.token, user }))
}
