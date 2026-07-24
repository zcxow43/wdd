/**
 * Base URL prefixed to every API request.
 *
 * Empty by default so requests are relative (e.g. `/api/currencies`) and get
 * routed through the Vite dev server proxy in development (see
 * vite.config.ts) or through a same-origin reverse proxy in production.
 * Set VITE_API_BASE_URL to point at a different origin (the backend must
 * then send appropriate CORS headers).
 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

/** Error shape returned by the backend's GlobalExceptionHandler. */
export interface ApiErrorBody {
  error?: string
  [key: string]: unknown
}

export class ApiError extends Error {
  readonly status: number
  readonly body: ApiErrorBody | null

  constructor(status: number, body: ApiErrorBody | null, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

/** Thrown when the request never reached the server (offline, DNS, CORS, etc). */
export class NetworkError extends Error {
  constructor(cause: unknown) {
    super('Network request failed')
    this.name = 'NetworkError'
    this.cause = cause
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      headers: { 'Content-Type': 'application/json' },
      ...init,
    })
  } catch (cause) {
    throw new NetworkError(cause)
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  const data = text ? JSON.parse(text) : null

  if (!response.ok) {
    throw new ApiError(response.status, data, data?.error ?? response.statusText)
  }

  return data as T
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
