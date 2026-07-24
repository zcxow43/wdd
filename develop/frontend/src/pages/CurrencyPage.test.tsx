import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
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
  active: true,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

const USD: Currency = {
  id: 2,
  code: 'USD',
  name: 'United States Dollar',
  nameZh: '美元',
  symbol: '$',
  decimalPlaces: 2,
  active: false,
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

describe('CurrencyPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('loads and renders currencies on mount', async () => {
    mockedApi.list.mockResolvedValue([TWD, USD])
    renderPage()

    expect(await screen.findByText('TWD')).toBeInTheDocument()
    expect(screen.getByText('USD')).toBeInTheDocument()
    expect(mockedApi.list).toHaveBeenCalledWith(undefined)
  })

  it('refetches with the active filter when the status filter changes', async () => {
    mockedApi.list.mockResolvedValue([TWD])
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('TWD')

    await user.selectOptions(screen.getByLabelText('狀態篩選'), 'ACTIVE')
    await waitFor(() => expect(mockedApi.list).toHaveBeenLastCalledWith(true))

    await user.selectOptions(screen.getByLabelText('狀態篩選'), 'INACTIVE')
    await waitFor(() => expect(mockedApi.list).toHaveBeenLastCalledWith(false))
  })

  it('shows the empty state when no currencies match', async () => {
    mockedApi.list.mockResolvedValue([])
    renderPage()

    expect(await screen.findByText('目前沒有符合條件的幣種')).toBeInTheDocument()
  })

  it('shows a network error toast when the initial load fails', async () => {
    mockedApi.list.mockRejectedValue(new NetworkError(new TypeError('fail')))
    renderPage()

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
  })

  it('creates a currency through the add modal and refreshes the table', async () => {
    mockedApi.list.mockResolvedValueOnce([TWD]).mockResolvedValueOnce([TWD, USD])
    mockedApi.create.mockResolvedValue(USD)
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('TWD')

    await user.click(screen.getByRole('button', { name: '+ Add' }))
    await user.type(screen.getByLabelText('代碼'), 'USD')
    await user.type(screen.getByLabelText('名稱'), 'United States Dollar')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    await waitFor(() => expect(mockedApi.create).toHaveBeenCalled())
    expect(await screen.findByText('USD')).toBeInTheDocument()
  })

  it('edits a currency with the code field disabled and refreshes the table', async () => {
    mockedApi.list.mockResolvedValue([TWD])
    mockedApi.update.mockResolvedValue({ ...TWD, name: 'Updated Name' })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('TWD')

    const row = screen.getByText('TWD').closest('tr')!
    await user.click(within(row).getByText('Edit'))

    const codeInput = screen.getByLabelText('代碼') as HTMLInputElement
    expect(codeInput).toBeDisabled()

    const nameInput = screen.getByLabelText('名稱')
    await user.clear(nameInput)
    await user.type(nameInput, 'Updated Name')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    await waitFor(() => expect(mockedApi.update).toHaveBeenCalledWith(1, expect.objectContaining({ name: 'Updated Name' })))
  })

  it('shows a not-found toast and refreshes when editing a currency that was already deleted', async () => {
    mockedApi.list.mockResolvedValue([TWD])
    mockedApi.update.mockRejectedValue(new ApiError(404, { error: 'Currency not found' }, 'Not Found'))
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('TWD')

    const row = screen.getByText('TWD').closest('tr')!
    await user.click(within(row).getByText('Edit'))
    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('幣種不存在，請重新整理頁面')).toBeInTheDocument()
  })

  it('deletes a currency after confirmation', async () => {
    mockedApi.list.mockResolvedValueOnce([TWD, USD]).mockResolvedValueOnce([USD])
    mockedApi.remove.mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('TWD')

    const row = screen.getByText('TWD').closest('tr')!
    await user.click(within(row).getByText('Delete'))

    expect(await screen.findByText('確定要刪除幣種 TWD 嗎？')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '確定' }))

    await waitFor(() => expect(mockedApi.remove).toHaveBeenCalledWith(1))
  })

  it('shows a not-found toast when deleting a currency that no longer exists', async () => {
    mockedApi.list.mockResolvedValue([TWD])
    mockedApi.remove.mockRejectedValue(new ApiError(404, { error: 'Currency not found' }, 'Not Found'))
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('TWD')

    const row = screen.getByText('TWD').closest('tr')!
    await user.click(within(row).getByText('Delete'))
    await user.click(screen.getByRole('button', { name: '確定' }))

    expect(await screen.findByText('幣種不存在，請重新整理頁面')).toBeInTheDocument()
  })
})
