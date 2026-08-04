const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

/** Thrown when the server responded, but with a non-2xx status code. */
export class ApiError extends Error {
  status: number
  body: unknown

  constructor(status: number, body: unknown) {
    const message =
      body && typeof body === 'object' && 'error' in body && typeof (body as { error?: unknown }).error === 'string'
        ? (body as { error: string }).error
        : `Request failed with status ${status}`
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

/** Thrown when the request never reached the server (offline, DNS, CORS, etc). */
export class NetworkError extends Error {
  constructor(cause?: unknown) {
    super('Network error')
    this.name = 'NetworkError'
    if (cause !== undefined) {
      this.cause = cause
    }
  }
}

async function parseBody(response: Response): Promise<unknown> {
  if (response.status === 204) {
    return undefined
  }
  const text = await response.text()
  if (!text) {
    return undefined
  }
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...init?.headers,
      },
    })
  } catch (cause) {
    throw new NetworkError(cause)
  }

  const body = await parseBody(response)

  if (!response.ok) {
    throw new ApiError(response.status, body)
  }

  return body as T
}

export function get<T>(path: string): Promise<T> {
  return request<T>(path, { method: 'GET' })
}

export function post<T>(path: string, data: unknown): Promise<T> {
  return request<T>(path, { method: 'POST', body: JSON.stringify(data) })
}

export function put<T>(path: string, data: unknown): Promise<T> {
  return request<T>(path, { method: 'PUT', body: JSON.stringify(data) })
}

export function del<T>(path: string): Promise<T> {
  return request<T>(path, { method: 'DELETE' })
}
