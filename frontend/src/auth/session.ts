import { reactive } from 'vue'

export type UserRole = 'SUPER_ADMIN' | 'ADMIN' | 'USER'

export interface UserProfile {
  id: string
  username: string
  displayName: string
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
    const raw = sessionStorage.getItem(storageKey)
    if (!raw) {
      return null
    }
    const stored = JSON.parse(raw) as Partial<StoredSession>
    if (!stored.token || !stored.user) {
      sessionStorage.removeItem(storageKey)
      return null
    }
    return stored as StoredSession
  } catch {
    sessionStorage.removeItem(storageKey)
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
  sessionStorage.setItem(storageKey, JSON.stringify({ token, user }))
}

export function clearSession() {
  authState.token = null
  authState.user = null
  sessionStorage.removeItem(storageKey)
}

export function refreshUser(user: UserProfile) {
  if (!authState.token) {
    return
  }
  authState.user = user
  sessionStorage.setItem(storageKey, JSON.stringify({ token: authState.token, user }))
}
