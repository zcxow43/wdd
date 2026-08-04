import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CurrencyPage } from './CurrencyPage'
import { ToastProvider } from '../components/ToastProvider'
import { currencyApi } from '../api/currencyApi'
import { ApiError, NetworkError } from '../api/client'
import type { Currency } from '../types/currency'

vi.mock('../api/currencyApi', () => ({
  currencyApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
  },
}))

const mockedApi = vi.mocked(currencyApi)

const TWD: Currency = {
  id: 1,
  code: 'TWD',
  name: 'New Taiwan Dollar',
  nameZh: '新台幣',
  symbol: 'NT$',
  decimalPlaces: 0,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

const USD: Currency = {
  id: 2,
  code: 'USD',
  name: 'US Dollar',
  nameZh: '美元',
  symbol: '$',
  decimalPlaces: 2,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

function renderPage() {
  return render(
    <ToastProvider>
      <CurrencyPage />
    </ToastProvider>,
  )
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe('CurrencyPage', () => {
  it('loads currencies from the API on mount with no filter params', async () => {
    mockedApi.list.mockResolvedValue([TWD, USD])

    renderPage()

    expect(await screen.findByText('TWD')).toBeInTheDocument()
    expect(screen.getByText('USD')).toBeInTheDocument()
    expect(mockedApi.list).toHaveBeenCalledWith()
  })

  it('renders no status filter on the page', async () => {
    mockedApi.list.mockResolvedValue([TWD])

    renderPage()
    await screen.findByText('TWD')

    expect(screen.queryByText('Active')).not.toBeInTheDocument()
    expect(screen.queryByText('Inactive')).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
  })

  it('shows an empty state when there are no currencies', async () => {
    mockedApi.list.mockResolvedValue([])

    renderPage()

    expect(await screen.findByText('目前沒有幣種資料')).toBeInTheDocument()
  })

  it('shows a network-error toast when the initial load fails', async () => {
    mockedApi.list.mockRejectedValue(new NetworkError())

    renderPage()

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
  })

  it('creates a currency through the add modal and refreshes the table', async () => {
    mockedApi.list.mockResolvedValueOnce([TWD]).mockResolvedValueOnce([TWD, USD])
    mockedApi.create.mockResolvedValue(USD)

    renderPage()
    await screen.findByText('TWD')

    await userEvent.click(screen.getByText('+ Add'))
    await userEvent.type(screen.getByLabelText('Code'), 'USD')
    await userEvent.type(screen.getByLabelText('Name'), 'US Dollar')
    await userEvent.type(screen.getByLabelText('中文名稱'), '美元')
    await userEvent.type(screen.getByLabelText('Symbol'), '$')
    await userEvent.type(screen.getByLabelText('Decimal Places'), '2')
    await userEvent.click(screen.getByText('儲存'))

    await waitFor(() => expect(mockedApi.create).toHaveBeenCalledWith({
      code: 'USD',
      name: 'US Dollar',
      nameZh: '美元',
      symbol: '$',
      decimalPlaces: 2,
    }))
    expect(await screen.findByText('USD')).toBeInTheDocument()
  })

  it('edits a currency through the edit modal with the code field disabled', async () => {
    mockedApi.list.mockResolvedValue([TWD])
    mockedApi.update.mockResolvedValue({ ...TWD, name: 'Updated Name' })

    renderPage()
    await screen.findByText('TWD')

    await userEvent.click(screen.getByText('編輯'))
    expect(screen.getByLabelText('Code')).toBeDisabled()

    await userEvent.clear(screen.getByLabelText('Name'))
    await userEvent.type(screen.getByLabelText('Name'), 'Updated Name')
    await userEvent.click(screen.getByText('儲存'))

    await waitFor(() =>
      expect(mockedApi.update).toHaveBeenCalledWith(1, {
        name: 'Updated Name',
        nameZh: '新台幣',
        symbol: 'NT$',
        decimalPlaces: 0,
      }),
    )
  })

  it('shows a confirmation dialog and deletes on confirm', async () => {
    mockedApi.list.mockResolvedValue([TWD])
    mockedApi.remove.mockResolvedValue(undefined)

    renderPage()
    await screen.findByText('TWD')

    await userEvent.click(screen.getByText('刪除'))
    expect(screen.getByText('確定要刪除幣種 TWD 嗎？')).toBeInTheDocument()

    await userEvent.click(screen.getByText('確定'))

    await waitFor(() => expect(mockedApi.remove).toHaveBeenCalledWith(1))
  })

  it('shows a 404 toast and refreshes when deleting an already-deleted currency', async () => {
    mockedApi.list.mockResolvedValue([TWD])
    mockedApi.remove.mockRejectedValue(
      new ApiError(404, { error: 'Currency not found', id: 1 }),
    )

    renderPage()
    await screen.findByText('TWD')

    await userEvent.click(screen.getByText('刪除'))
    await userEvent.click(screen.getByText('確定'))

    expect(await screen.findByText('幣種不存在，請重新整理頁面')).toBeInTheDocument()
    expect(mockedApi.list).toHaveBeenCalledTimes(2)
  })

  it('shows an in-use toast and keeps the row when the currency is referenced by a currency pair', async () => {
    mockedApi.list.mockResolvedValue([TWD])
    mockedApi.remove.mockRejectedValue(
      new ApiError(409, {
        error: 'Currency is referenced by one or more currency pairs and cannot be deleted',
        id: 1,
      }),
    )

    renderPage()
    await screen.findByText('TWD')

    await userEvent.click(screen.getByText('刪除'))
    await userEvent.click(screen.getByText('確定'))

    expect(await screen.findByText('此幣種已配置於幣種對，無法刪除')).toBeInTheDocument()
    expect(mockedApi.list).toHaveBeenCalledTimes(1)
    expect(screen.getByText('TWD')).toBeInTheDocument()
  })

  it('filters the table client-side via the search box', async () => {
    mockedApi.list.mockResolvedValue([TWD, USD])

    renderPage()
    await screen.findByText('TWD')

    await userEvent.type(screen.getByPlaceholderText('Search...'), 'USD')

    expect(screen.queryByText('TWD')).not.toBeInTheDocument()
    expect(screen.getByText('USD')).toBeInTheDocument()
  })
})
