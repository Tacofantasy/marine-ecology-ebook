import { getToken } from '../auth/session'

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
  if (requiresAuth) {
    const token = getToken()
    if (token) {
      headers.set('satoken', token)
    }
  }

  const response = await fetch(path, { ...options, headers })
  let body: ApiResponse<T>
  try {
    body = (await response.json()) as ApiResponse<T>
  } catch {
    throw new ApiError('服务响应异常，请稍后重试。', 50000, response.status)
  }

  if (!response.ok || body.code !== 0) {
    throw new ApiError(body.message || '请求失败，请稍后重试。', body.code, response.status)
  }
  return body.data
}
