import { clearSession, getToken } from '../auth/session'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export class ApiError extends Error {
  readonly code: number
  readonly status: number

  constructor(
    message: string,
    code: number,
    status: number,
  ) {
    super(message)
    this.code = code
    this.status = status
  }
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
  requiresAuth = false,
): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData
  if (options.body && !isFormData) {
    headers.set('Content-Type', 'application/json')
  }
  const token = requiresAuth ? getToken() : null
  if (token) headers.set('satoken', token)

  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), 20000)
  const abort = () => controller.abort()
  options.signal?.addEventListener('abort', abort, { once: true })
  if (options.signal?.aborted) controller.abort()
  try {
    const response = await fetch(path, { ...options, headers, signal: controller.signal })
    if (response.status === 401 && token && token === getToken()) clearSession()
    let body: ApiResponse<T>
    try {
      body = (await response.json()) as ApiResponse<T>
    } catch {
      throw new ApiError(controller.signal.aborted ? '请求超时，请重试。' : '服务响应异常，请稍后重试。', 50000, response.status)
    }
    if (!body || typeof body.code !== 'number') {
      throw new ApiError('服务响应异常，请稍后重试。', 50000, response.status)
    }
    if (!response.ok || body.code !== 0) {
      if (body.code === 40101 && token && token === getToken()) clearSession()
      throw new ApiError(body.message || '请求失败，请稍后重试。', body.code, response.status)
    }
    return body.data
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError(controller.signal.aborted
      ? '请求超时或已取消，请重试。' : '无法连接服务，请检查网络后重试。', 50000, 0)
  } finally {
    window.clearTimeout(timeout)
    options.signal?.removeEventListener('abort', abort)
  }
}
