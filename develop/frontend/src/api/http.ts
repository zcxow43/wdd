// Thin fetch wrapper. All API calls go through the `/api` prefix, which the
// Vite dev server proxies to the backend (see vite.config.ts). In production
// this assumes `/api` is served from the same origin (e.g. via a reverse proxy).
const API_BASE = '/api'

export class ApiError extends Error {
  status: number
  body: unknown

  constructor(status: number, message: string, body?: unknown) {
    super(message)
    this.status = status
    this.body = body
    this.name = 'ApiError'
  }
}

export async function apiRequest<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
    ...init,
  })

  if (!response.ok) {
    let body: unknown
    try {
      body = await response.json()
    } catch {
      body = undefined
    }

    let message = `Request to ${path} failed with status ${response.status}`
    if (body && typeof body === 'object') {
      const { error, message: bodyMessage } = body as {
        error?: string
        message?: string
      }
      message = error ?? bodyMessage ?? message
    }

    throw new ApiError(response.status, message, body)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}
