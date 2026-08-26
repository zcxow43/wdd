import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ExchangeRateSyncPage from './ExchangeRateSyncPage'
import type { Brand } from '../api/brands'
import { fetchBrands } from '../api/brands'
import type { ExchangeRateLatest } from '../api/exchangeRates'
import {
  fetchLatestExchangeRates,
  syncExchangeRates,
} from '../api/exchangeRates'
import { ApiError } from '../api/http'

vi.mock('../api/brands', () => ({
  fetchBrands: vi.fn(),
}))

vi.mock('../api/exchangeRates', () => ({
  fetchLatestExchangeRates: vi.fn(),
  syncExchangeRates: vi.fn(),
}))

const mockedFetchBrands = vi.mocked(fetchBrands)
const mockedFetchLatest = vi.mocked(fetchLatestExchangeRates)
const mockedSync = vi.mocked(syncExchangeRates)

function makeBrands(): Brand[] {
  return [
    {
      id: 1,
      code: 'au',
      name: 'AU Brand',
      active: true,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    },
    {
      id: 2,
      code: 'uk',
      name: 'UK Brand',
      active: true,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    },
  ]
}

function makeRates(brandId: number, brandCode: string): ExchangeRateLatest[] {
  return [
    {
      currencyPairDefinitionId: 1,
      baseCurrencyCode: 'USD',
      quoteCurrencyCode: 'JPY',
      precision: 3,
      brandId,
      brandCode,
      rate: 149.85,
      depositRate: 149.9,
      withdrawalRate: 150.1,
      rateMinute: '2026-08-25T14:23:00',
      source: 'open.er-api.com',
    },
    {
      currencyPairDefinitionId: 2,
      baseCurrencyCode: 'USD',
      quoteCurrencyCode: 'TWD',
      precision: 4,
      brandId,
      brandCode,
      rate: null,
      depositRate: null,
      withdrawalRate: null,
      rateMinute: null,
      source: null,
    },
  ]
}

function getDataRows() {
  return screen.getAllByRole('row').slice(1)
}

function getSyncButton() {
  return screen.getByRole('button', { name: /同步/ })
}

describe('ExchangeRateSyncPage', () => {
  beforeEach(() => {
    mockedFetchBrands.mockReset()
    mockedFetchLatest.mockReset()
    mockedSync.mockReset()
  })

  it('loads brands, selects the first by default, and displays its rates with placeholders', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchLatest.mockImplementation((brandId) =>
      Promise.resolve(makeRates(brandId, brandId === 1 ? 'au' : 'uk')),
    )
    render(<ExchangeRateSyncPage />)

    await waitFor(() => expect(getDataRows()).toHaveLength(2))
    expect(mockedFetchLatest).toHaveBeenCalledWith(1)

    const tabs = screen.getAllByRole('tab')
    expect(tabs).toHaveLength(2)
    expect(tabs[0]).toHaveAttribute('aria-selected', 'true')
    expect(tabs[1]).toHaveAttribute('aria-selected', 'false')

    const rows = getDataRows()
    expect(within(rows[0]).getByText('USD')).toBeInTheDocument()
    expect(within(rows[0]).getByText('JPY')).toBeInTheDocument()
    expect(within(rows[0]).getByText('149.850')).toBeInTheDocument()
    expect(within(rows[0]).getByText('149.900')).toBeInTheDocument()
    expect(within(rows[0]).getByText('150.100')).toBeInTheDocument()
    expect(within(rows[0]).getByText('2026-08-25 14:23')).toBeInTheDocument()
    expect(within(rows[0]).getByText('open.er-api.com')).toBeInTheDocument()

    expect(within(rows[1]).getByText('尚未同步')).toBeInTheDocument()
    expect(within(rows[1]).getAllByText('-')).toHaveLength(4)
  })

  it('shows a retry button on brand load failure', async () => {
    mockedFetchBrands.mockRejectedValue(new Error('network error'))
    render(<ExchangeRateSyncPage />)

    await waitFor(() =>
      expect(screen.getByText('載入品牌清單失敗，請稍後再試。')).toBeInTheDocument(),
    )

    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchLatest.mockResolvedValue(makeRates(1, 'au'))
    await userEvent
      .setup({ delay: null })
      .click(screen.getByRole('button', { name: '重試' }))

    await waitFor(() => expect(getDataRows()).toHaveLength(2))
  })

  it('shows a retry button on rate list load failure for the selected brand', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchLatest.mockRejectedValue(new Error('network error'))
    render(<ExchangeRateSyncPage />)

    await waitFor(() =>
      expect(screen.getByText('載入匯率清單失敗，請稍後再試。')).toBeInTheDocument(),
    )

    mockedFetchLatest.mockResolvedValue(makeRates(1, 'au'))
    await userEvent
      .setup({ delay: null })
      .click(screen.getByRole('button', { name: '重試' }))

    await waitFor(() => expect(getDataRows()).toHaveLength(2))
  })

  it('switching the selected brand reloads the table for that brand', async () => {
    const user = userEvent.setup({ delay: null })
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchLatest.mockImplementation((brandId) =>
      Promise.resolve(makeRates(brandId, brandId === 1 ? 'au' : 'uk')),
    )
    render(<ExchangeRateSyncPage />)

    await waitFor(() => expect(getDataRows()).toHaveLength(2))
    expect(mockedFetchLatest).toHaveBeenCalledWith(1)

    await user.click(screen.getByRole('tab', { name: 'uk' }))

    await waitFor(() => expect(mockedFetchLatest).toHaveBeenCalledWith(2))
    await waitFor(() =>
      expect(screen.getByRole('tab', { name: 'uk' })).toHaveAttribute(
        'aria-selected',
        'true',
      ),
    )
  })

  it('filters rows client-side by base/quote currency code, case-insensitively, with no extra request', async () => {
    const user = userEvent.setup({ delay: null })
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchLatest.mockResolvedValue(makeRates(1, 'au'))
    render(<ExchangeRateSyncPage />)

    await waitFor(() => expect(getDataRows()).toHaveLength(2))
    const callsBeforeSearch = mockedFetchLatest.mock.calls.length

    await user.type(
      screen.getByLabelText('搜尋基準幣或報價幣'),
      'jpy',
    )
    await waitFor(() => expect(getDataRows()).toHaveLength(1))
    expect(within(getDataRows()[0]).getByText('JPY')).toBeInTheDocument()
    expect(mockedFetchLatest.mock.calls.length).toBe(callsBeforeSearch)

    await user.clear(screen.getByLabelText('搜尋基準幣或報價幣'))
    await waitFor(() => expect(getDataRows()).toHaveLength(2))
  })

  it('search input stays enabled during sync and cooldown', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchLatest.mockResolvedValue(makeRates(1, 'au'))
    mockedSync.mockResolvedValue({
      syncedAt: '2026-08-25T14:24:00',
      updated: [],
      skipped: [],
    })
    render(<ExchangeRateSyncPage />)

    await waitFor(() => expect(getDataRows()).toHaveLength(2))
    fireEvent.click(getSyncButton())
    expect(screen.getByLabelText('搜尋基準幣或報價幣')).not.toBeDisabled()

    await waitFor(() => expect(getSyncButton()).toBeDisabled())
    expect(screen.getByLabelText('搜尋基準幣或報價幣')).not.toBeDisabled()
  })

  it('syncs with no request body, reloads the selected brand, shows a full-scope toast, re-applies search, and starts a 60s cooldown', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchLatest.mockResolvedValue(makeRates(1, 'au'))
    mockedSync.mockResolvedValue({
      syncedAt: '2026-08-25T14:24:00',
      updated: [
        {
          currencyPairDefinitionId: 1,
          baseCurrencyCode: 'USD',
          quoteCurrencyCode: 'JPY',
          brandId: 1,
          brandCode: 'au',
          rate: 149.9,
          depositRate: 149.95,
          withdrawalRate: 150.15,
        },
        {
          currencyPairDefinitionId: 1,
          baseCurrencyCode: 'USD',
          quoteCurrencyCode: 'JPY',
          brandId: 2,
          brandCode: 'uk',
          rate: 149.9,
          depositRate: 149.95,
          withdrawalRate: 150.15,
        },
      ],
      skipped: [],
    })
    render(<ExchangeRateSyncPage />)

    await waitFor(() => expect(getDataRows()).toHaveLength(2))

    await userEvent
      .setup({ delay: null })
      .type(screen.getByLabelText('搜尋基準幣或報價幣'), 'jpy')
    await waitFor(() => expect(getDataRows()).toHaveLength(1))

    vi.useFakeTimers()
    try {
      fireEvent.click(getSyncButton())
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0)
      })

      expect(mockedSync).toHaveBeenCalledWith()
      // the full cross-brand count (2), not the selected brand's count
      expect(screen.getByText('已同步 2 筆匯率')).toBeInTheDocument()
      expect(screen.getByText('60 秒後可同步')).toBeInTheDocument()
      expect(getSyncButton()).toBeDisabled()
      // search text survives the reload and still filters
      expect(getDataRows()).toHaveLength(1)
      expect(within(getDataRows()[0]).getByText('JPY')).toBeInTheDocument()

      await act(async () => {
        await vi.advanceTimersByTimeAsync(1000)
      })
      expect(screen.getByText('59 秒後可同步')).toBeInTheDocument()

      for (let i = 0; i < 59; i += 1) {
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000)
        })
      }
      expect(getSyncButton()).toHaveTextContent('同步最新匯率')
      expect(getSyncButton()).not.toBeDisabled()
    } finally {
      vi.useRealTimers()
    }
  })

  it('shows a message reflecting skipped rows', async () => {
    const user = userEvent.setup({ delay: null })
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchLatest.mockResolvedValue(makeRates(1, 'au'))
    mockedSync.mockResolvedValue({
      syncedAt: '2026-08-25T14:24:00',
      updated: [
        {
          currencyPairDefinitionId: 1,
          baseCurrencyCode: 'USD',
          quoteCurrencyCode: 'JPY',
          brandId: 1,
          brandCode: 'au',
          rate: 149.9,
          depositRate: 149.95,
          withdrawalRate: 150.15,
        },
      ],
      skipped: [
        {
          currencyPairDefinitionId: 2,
          baseCurrencyCode: 'USD',
          quoteCurrencyCode: 'TWD',
          reason: 'not returned by provider',
        },
      ],
    })
    render(<ExchangeRateSyncPage />)

    await waitFor(() => expect(getDataRows()).toHaveLength(2))
    await user.click(getSyncButton())

    await waitFor(() =>
      expect(
        screen.getByText('已同步 1 筆匯率，1 筆供應商未提供報價'),
      ).toBeInTheDocument(),
    )
  })

  it('on 429, shows the server message and restarts the countdown from retryAfterSeconds', async () => {
    const user = userEvent.setup({ delay: null })
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchLatest.mockResolvedValue(makeRates(1, 'au'))
    mockedSync.mockRejectedValue(
      new ApiError(429, '匯率同步一分鐘內僅能執行一次，請稍後再試', {
        error: '匯率同步一分鐘內僅能執行一次，請稍後再試',
        retryAfterSeconds: 37,
      }),
    )
    render(<ExchangeRateSyncPage />)

    await waitFor(() => expect(getDataRows()).toHaveLength(2))
    await user.click(getSyncButton())

    await waitFor(() =>
      expect(
        screen.getByText('匯率同步一分鐘內僅能執行一次，請稍後再試'),
      ).toBeInTheDocument(),
    )
    await waitFor(() =>
      expect(screen.getByText('37 秒後可同步')).toBeInTheDocument(),
    )
    expect(getSyncButton()).toBeDisabled()
  })

  it('on 502, shows the generic error toast, keeps the table, and re-enables immediately', async () => {
    const user = userEvent.setup({ delay: null })
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchLatest.mockResolvedValue(makeRates(1, 'au'))
    mockedSync.mockRejectedValue(
      new ApiError(502, 'Failed to fetch rates from external provider'),
    )
    render(<ExchangeRateSyncPage />)

    await waitFor(() => expect(getDataRows()).toHaveLength(2))
    await user.click(getSyncButton())

    await waitFor(() =>
      expect(screen.getByText('同步失敗，請稍後再試')).toBeInTheDocument(),
    )
    expect(getSyncButton()).not.toBeDisabled()
    expect(getSyncButton()).toHaveTextContent('同步最新匯率')
    expect(getDataRows()).toHaveLength(2)
  })

  it('on a network failure, shows the generic error toast and re-enables immediately', async () => {
    const user = userEvent.setup({ delay: null })
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchLatest.mockResolvedValue(makeRates(1, 'au'))
    mockedSync.mockRejectedValue(new TypeError('Failed to fetch'))
    render(<ExchangeRateSyncPage />)

    await waitFor(() => expect(getDataRows()).toHaveLength(2))
    await user.click(getSyncButton())

    await waitFor(() =>
      expect(screen.getByText('同步失敗，請稍後再試')).toBeInTheDocument(),
    )
    expect(getSyncButton()).not.toBeDisabled()
  })
})
