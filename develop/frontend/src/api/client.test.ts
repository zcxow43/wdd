import { describe, it, expect, vi, afterEach } from 'vitest'
import { ApiError, NetworkError, get, post, del } from './client'

describe('api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('resolves parsed JSON on a successful GET', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify([{ id: 1 }]), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const result = await get<{ id: number }[]>('/api/currencies')

    expect(result).toEqual([{ id: 1 }])
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/currencies',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('resolves undefined on a 204 No Content', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await del<undefined>('/api/currencies/1')

    expect(result).toBeUndefined()
  })

  it('throws ApiError with status 409 and parsed body on conflict', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ error: 'Currency code already exists', code: 'KRW' }), {
        status: 409,
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(post('/api/currencies', { code: 'KRW' })).rejects.toMatchObject({
      name: 'ApiError',
      status: 409,
      body: { error: 'Currency code already exists', code: 'KRW' },
    })
  })

  it('throws ApiError with status 404', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ error: 'Currency not found', id: 999 }), { status: 404 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(get('/api/currencies/999')).rejects.toBeInstanceOf(ApiError)
  })

  it('throws NetworkError when fetch rejects', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    vi.stubGlobal('fetch', fetchMock)

    await expect(get('/api/currencies')).rejects.toBeInstanceOf(NetworkError)
  })
})
