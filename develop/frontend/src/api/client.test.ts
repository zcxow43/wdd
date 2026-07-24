import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient, ApiError, NetworkError } from './client'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('apiClient', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('returns parsed JSON on success', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse([{ id: 1, code: 'TWD' }])))

    const result = await apiClient.get<{ id: number; code: string }[]>('/api/currencies')

    expect(result).toEqual([{ id: 1, code: 'TWD' }])
  })

  it('returns undefined for 204 No Content', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))

    const result = await apiClient.delete('/api/currencies/1')

    expect(result).toBeUndefined()
  })

  it('throws ApiError with parsed body on non-2xx response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ error: 'Currency code already exists', code: 'KRW' }, 409)),
    )

    await expect(apiClient.post('/api/currencies', {})).rejects.toMatchObject({
      status: 409,
      body: { error: 'Currency code already exists', code: 'KRW' },
    })
  })

  it('throws NetworkError when fetch rejects', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    await expect(apiClient.get('/api/currencies')).rejects.toBeInstanceOf(NetworkError)
  })

  it('ApiError is an instance of Error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ error: 'Currency not found' }, 404)))

    try {
      await apiClient.get('/api/currencies/999')
      expect.unreachable()
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).status).toBe(404)
    }
  })
})
