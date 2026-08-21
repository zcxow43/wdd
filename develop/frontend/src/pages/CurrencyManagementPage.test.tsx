import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CurrencyManagementPage from './CurrencyManagementPage'
import type { Currency } from '../api/currencies'
import {
  createCurrency,
  deleteCurrency,
  fetchCurrencies,
  updateCurrency,
} from '../api/currencies'
import { ApiError } from '../api/http'

vi.mock('../api/currencies', () => ({
  fetchCurrencies: vi.fn(),
  createCurrency: vi.fn(),
  updateCurrency: vi.fn(),
  deleteCurrency: vi.fn(),
}))

const mockedFetchCurrencies = vi.mocked(fetchCurrencies)
const mockedCreateCurrency = vi.mocked(createCurrency)
const mockedUpdateCurrency = vi.mocked(updateCurrency)
const mockedDeleteCurrency = vi.mocked(deleteCurrency)

function makeCurrencies(): Currency[] {
  return [
    {
      id: 1,
      code: 'USD',
      name: 'US Dollar',
      symbol: '$',
      decimalPlaces: 2,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    },
    {
      id: 2,
      code: 'JPY',
      name: 'Japanese Yen',
      symbol: '¥',
      decimalPlaces: 0,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    },
  ]
}

function getDataRows() {
  return screen.getAllByRole('row').slice(1)
}

function findRowByCode(code: string): HTMLElement {
  const row = getDataRows().find(
    (r) => within(r).getAllByRole('cell')[0].textContent === code,
  )
  if (!row) {
    throw new Error(`row for code ${code} not found`)
  }
  return row
}

describe('CurrencyManagementPage', () => {
  beforeEach(() => {
    mockedFetchCurrencies.mockReset()
    mockedCreateCurrency.mockReset()
    mockedUpdateCurrency.mockReset()
    mockedDeleteCurrency.mockReset()
  })

  it('loads and displays all currencies from GET /api/currencies', async () => {
    mockedFetchCurrencies.mockResolvedValue(makeCurrencies())

    render(<CurrencyManagementPage />)

    expect(await screen.findByText('幣別管理')).toBeInTheDocument()
    await screen.findByRole('table')

    const rows = getDataRows()
    expect(rows).toHaveLength(2)

    const usdRow = findRowByCode('USD')
    const cells = within(usdRow).getAllByRole('cell')
    expect(cells[1].textContent).toBe('US Dollar')
    expect(cells[2].textContent).toBe('$')
    expect(cells[3].textContent).toBe('2')
  })

  it('shows an inline error with a retry button when the list fails to load', async () => {
    mockedFetchCurrencies.mockRejectedValueOnce(new Error('network error'))

    render(<CurrencyManagementPage />)

    expect(await screen.findByText(/載入幣種清單失敗/)).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()

    mockedFetchCurrencies.mockResolvedValueOnce(makeCurrencies())
    await userEvent.click(screen.getByRole('button', { name: '重試' }))

    expect(await screen.findByRole('table')).toBeInTheDocument()
  })

  it('creates a new currency via the modal form', async () => {
    mockedFetchCurrencies.mockResolvedValue(makeCurrencies())
    const created: Currency = {
      id: 3,
      code: 'EUR',
      name: 'Euro',
      symbol: '€',
      decimalPlaces: 2,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    }
    mockedCreateCurrency.mockResolvedValue(created)

    render(<CurrencyManagementPage />)
    await screen.findByRole('table')

    await userEvent.click(screen.getByRole('button', { name: '+ 新增幣種' }))

    await userEvent.type(screen.getByLabelText('代碼'), 'EUR')
    await userEvent.type(screen.getByLabelText('名稱'), 'Euro')
    await userEvent.type(screen.getByLabelText('符號'), '€')
    await userEvent.type(screen.getByLabelText('小數位數'), '2')

    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    expect(mockedCreateCurrency).toHaveBeenCalledWith({
      code: 'EUR',
      name: 'Euro',
      symbol: '€',
      decimalPlaces: 2,
    })

    await screen.findByText('幣種已新增')
    expect(findRowByCode('EUR')).toBeInTheDocument()
  })

  it('shows inline duplicate-code error on 409 without closing the modal', async () => {
    mockedFetchCurrencies.mockResolvedValue(makeCurrencies())
    mockedCreateCurrency.mockRejectedValue(new ApiError(409, 'conflict'))

    render(<CurrencyManagementPage />)
    await screen.findByRole('table')

    await userEvent.click(screen.getByRole('button', { name: '+ 新增幣種' }))
    await userEvent.type(screen.getByLabelText('代碼'), 'USD')
    await userEvent.type(screen.getByLabelText('名稱'), 'US Dollar')
    await userEvent.type(screen.getByLabelText('符號'), '$')
    await userEvent.type(screen.getByLabelText('小數位數'), '2')
    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('此代碼已存在')).toBeInTheDocument()
    expect(screen.getByLabelText('代碼')).toBeInTheDocument()
  })

  it('edits an existing currency with 代碼 read-only', async () => {
    mockedFetchCurrencies.mockResolvedValue(makeCurrencies())
    const updated: Currency = {
      id: 1,
      code: 'USD',
      name: 'United States Dollar',
      symbol: '$',
      decimalPlaces: 2,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-02T00:00:00',
    }
    mockedUpdateCurrency.mockResolvedValue(updated)

    render(<CurrencyManagementPage />)
    await screen.findByRole('table')

    const usdRow = findRowByCode('USD')
    await userEvent.click(within(usdRow).getByRole('button', { name: '編輯' }))

    const codeInput = screen.getByLabelText('代碼') as HTMLInputElement
    expect(codeInput).toBeDisabled()
    expect(codeInput.value).toBe('USD')

    const nameInput = screen.getByLabelText('名稱')
    await userEvent.clear(nameInput)
    await userEvent.type(nameInput, 'United States Dollar')

    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    expect(mockedUpdateCurrency).toHaveBeenCalledWith(1, {
      name: 'United States Dollar',
      symbol: '$',
      decimalPlaces: 2,
    })

    await screen.findByText('幣種已更新')
    await waitFor(() => {
      expect(
        within(findRowByCode('USD')).getByText('United States Dollar'),
      ).toBeInTheDocument()
    })
  })

  it('deletes a currency after confirmation', async () => {
    mockedFetchCurrencies.mockResolvedValue(makeCurrencies())
    mockedDeleteCurrency.mockResolvedValue(undefined)

    render(<CurrencyManagementPage />)
    await screen.findByRole('table')

    const jpyRow = findRowByCode('JPY')
    await userEvent.click(within(jpyRow).getByRole('button', { name: '刪除' }))

    await screen.findByText(/確定要刪除幣種「JPY Japanese Yen」嗎/)
    await userEvent.click(
      screen.getAllByRole('button', { name: '刪除' }).at(-1)!,
    )

    expect(mockedDeleteCurrency).toHaveBeenCalledWith(2)
    await screen.findByText('幣種已刪除')
    expect(screen.queryByText('JPY')).not.toBeInTheDocument()
  })

  it('shows an error toast and keeps the row when delete fails', async () => {
    mockedFetchCurrencies.mockResolvedValue(makeCurrencies())
    mockedDeleteCurrency.mockRejectedValue(new Error('failed'))

    render(<CurrencyManagementPage />)
    await screen.findByRole('table')

    const jpyRow = findRowByCode('JPY')
    await userEvent.click(within(jpyRow).getByRole('button', { name: '刪除' }))
    await userEvent.click(
      screen.getAllByRole('button', { name: '刪除' }).at(-1)!,
    )

    await screen.findByText('刪除失敗，請稍後再試')
    expect(findRowByCode('JPY')).toBeInTheDocument()
  })
})
