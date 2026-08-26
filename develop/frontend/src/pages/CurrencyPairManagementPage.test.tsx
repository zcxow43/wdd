import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CurrencyPairManagementPage from './CurrencyPairManagementPage'
import type {
  CurrencyPair,
  CurrencyPairDefinition,
  CurrencyPairDefinitionCreateResponse,
} from '../api/currencyPairDefinitions'
import {
  createCurrencyPairDefinition,
  deleteCurrencyPairDefinition,
  fetchCurrencyPairDefinitions,
  fetchCurrencyPairsByDefinition,
  updateCurrencyPairDefinitionPrecision,
} from '../api/currencyPairDefinitions'
import type { Currency } from '../api/currencies'
import { fetchCurrencies } from '../api/currencies'
import { ApiError } from '../api/http'

vi.mock('../api/currencyPairDefinitions', () => ({
  fetchCurrencyPairDefinitions: vi.fn(),
  createCurrencyPairDefinition: vi.fn(),
  updateCurrencyPairDefinitionPrecision: vi.fn(),
  deleteCurrencyPairDefinition: vi.fn(),
  fetchCurrencyPairsByDefinition: vi.fn(),
}))

vi.mock('../api/currencies', () => ({
  fetchCurrencies: vi.fn(),
}))

const mockedFetchDefinitions = vi.mocked(fetchCurrencyPairDefinitions)
const mockedCreateDefinition = vi.mocked(createCurrencyPairDefinition)
const mockedUpdatePrecision = vi.mocked(updateCurrencyPairDefinitionPrecision)
const mockedDeleteDefinition = vi.mocked(deleteCurrencyPairDefinition)
const mockedFetchPairsByDefinition = vi.mocked(fetchCurrencyPairsByDefinition)
const mockedFetchCurrencies = vi.mocked(fetchCurrencies)

function makeDefinitions(): CurrencyPairDefinition[] {
  return [
    {
      id: 1,
      baseCurrencyId: 1,
      baseCurrencyCode: 'USD',
      quoteCurrencyId: 2,
      quoteCurrencyCode: 'JPY',
      precision: 4,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    },
    {
      id: 2,
      baseCurrencyId: 1,
      baseCurrencyCode: 'USD',
      quoteCurrencyId: 3,
      quoteCurrencyCode: 'EUR',
      precision: 2,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    },
  ]
}

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
    {
      id: 3,
      code: 'EUR',
      name: 'Euro',
      symbol: '€',
      decimalPlaces: 2,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    },
  ]
}

function makePairs(definitionId: number, active: number, total: number): CurrencyPair[] {
  const pairs: CurrencyPair[] = []
  for (let i = 0; i < total; i += 1) {
    pairs.push({
      id: definitionId * 100 + i,
      currencyPairDefinitionId: definitionId,
      brandId: i + 1,
      brandCode: `b${i + 1}`,
      rateType: 'AUTO',
      rate: null,
      active: i < active,
      depositRate: null,
      withdrawalRate: null,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    })
  }
  return pairs
}

function getDataRows() {
  return screen.getAllByRole('row').slice(1)
}

function findRowByCodes(base: string, quote: string): HTMLElement {
  const row = getDataRows().find((r) => {
    const cells = within(r).getAllByRole('cell')
    return cells[0].textContent === base && cells[1].textContent === quote
  })
  if (!row) {
    throw new Error(`row for ${base}/${quote} not found`)
  }
  return row
}

describe('CurrencyPairManagementPage', () => {
  beforeEach(() => {
    mockedFetchDefinitions.mockReset()
    mockedCreateDefinition.mockReset()
    mockedUpdatePrecision.mockReset()
    mockedDeleteDefinition.mockReset()
    mockedFetchPairsByDefinition.mockReset()
    mockedFetchCurrencies.mockReset()
    mockedFetchCurrencies.mockResolvedValue(makeCurrencies())
  })

  it('loads and displays all definitions with base/quote/precision/啟用品牌數', async () => {
    mockedFetchDefinitions.mockResolvedValue(makeDefinitions())
    mockedFetchPairsByDefinition.mockImplementation((id) =>
      Promise.resolve(makePairs(id, id === 1 ? 2 : 0, 7)),
    )

    render(<CurrencyPairManagementPage />)

    expect(await screen.findByText('幣別對管理')).toBeInTheDocument()
    await screen.findByRole('table')

    const rows = getDataRows()
    expect(rows).toHaveLength(2)

    const usdJpyRow = findRowByCodes('USD', 'JPY')
    const cells = within(usdJpyRow).getAllByRole('cell')
    expect(cells[2].textContent).toBe('4')

    await within(usdJpyRow).findByText('2 / 7')

    const usdEurRow = findRowByCodes('USD', 'EUR')
    await within(usdEurRow).findByText('0 / 7')
  })

  it('shows an inline error with a retry button when the list fails to load', async () => {
    mockedFetchDefinitions.mockRejectedValueOnce(new Error('network error'))

    render(<CurrencyPairManagementPage />)

    expect(
      await screen.findByText(/載入幣種對定義清單失敗/),
    ).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()

    mockedFetchDefinitions.mockResolvedValueOnce(makeDefinitions())
    mockedFetchPairsByDefinition.mockResolvedValue(makePairs(1, 0, 7))
    await userEvent.click(screen.getByRole('button', { name: '重試' }))

    expect(await screen.findByRole('table')).toBeInTheDocument()
  })

  it('creates a new definition via the modal form and shows the fan-out count in the toast', async () => {
    mockedFetchDefinitions.mockResolvedValue(makeDefinitions())
    mockedFetchPairsByDefinition.mockResolvedValue(makePairs(1, 0, 7))

    const created: CurrencyPairDefinitionCreateResponse = {
      id: 3,
      baseCurrencyId: 2,
      baseCurrencyCode: 'JPY',
      quoteCurrencyId: 3,
      quoteCurrencyCode: 'EUR',
      precision: 4,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
      currencyPairs: makePairs(3, 0, 7),
    }
    mockedCreateDefinition.mockResolvedValue(created)

    render(<CurrencyPairManagementPage />)
    await screen.findByRole('table')

    await userEvent.click(
      screen.getByRole('button', { name: '+ 新增幣種對' }),
    )

    await userEvent.selectOptions(screen.getByLabelText('基準幣'), '2')
    await userEvent.selectOptions(screen.getByLabelText('報價幣'), '3')

    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    expect(mockedCreateDefinition).toHaveBeenCalledWith({
      baseCurrencyId: 2,
      quoteCurrencyId: 3,
      precision: 4,
    })

    await screen.findByText('幣種對已新增，已為 7 個品牌建立品牌幣種對')
    const newRow = findRowByCodes('JPY', 'EUR')
    await within(newRow).findByText('0 / 7')
  })

  it('shows inline duplicate-pair error on 409 without closing the modal', async () => {
    mockedFetchDefinitions.mockResolvedValue(makeDefinitions())
    mockedFetchPairsByDefinition.mockResolvedValue(makePairs(1, 0, 7))
    mockedCreateDefinition.mockRejectedValue(new ApiError(409, 'conflict'))

    render(<CurrencyPairManagementPage />)
    await screen.findByRole('table')

    await userEvent.click(
      screen.getByRole('button', { name: '+ 新增幣種對' }),
    )
    await userEvent.selectOptions(screen.getByLabelText('基準幣'), '1')
    await userEvent.selectOptions(screen.getByLabelText('報價幣'), '2')
    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('此幣種對已存在')).toBeInTheDocument()
    expect(screen.getByLabelText('報價幣')).toBeInTheDocument()
  })

  it('edits an existing definition with 基準幣/報價幣 read-only', async () => {
    mockedFetchDefinitions.mockResolvedValue(makeDefinitions())
    mockedFetchPairsByDefinition.mockResolvedValue(makePairs(1, 0, 7))
    const updated: CurrencyPairDefinition = {
      id: 1,
      baseCurrencyId: 1,
      baseCurrencyCode: 'USD',
      quoteCurrencyId: 2,
      quoteCurrencyCode: 'JPY',
      precision: 6,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-02T00:00:00',
    }
    mockedUpdatePrecision.mockResolvedValue(updated)

    render(<CurrencyPairManagementPage />)
    await screen.findByRole('table')

    const row = findRowByCodes('USD', 'JPY')
    await userEvent.click(within(row).getByRole('button', { name: '編輯' }))

    const baseInput = screen.getByLabelText('基準幣') as HTMLInputElement
    expect(baseInput).toBeDisabled()
    expect(baseInput.value).toBe('USD')
    const quoteInput = screen.getByLabelText('報價幣') as HTMLInputElement
    expect(quoteInput).toBeDisabled()
    expect(quoteInput.value).toBe('JPY')

    const precisionInput = screen.getByLabelText('精度')
    await userEvent.clear(precisionInput)
    await userEvent.type(precisionInput, '6')

    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    expect(mockedUpdatePrecision).toHaveBeenCalledWith(1, 6)

    await screen.findByText('幣種對已更新')
    await waitFor(() => {
      expect(
        within(findRowByCodes('USD', 'JPY')).getByText('6'),
      ).toBeInTheDocument()
    })
  })

  it('disables 刪除 with a tooltip when 啟用品牌數 has active brands', async () => {
    mockedFetchDefinitions.mockResolvedValue(makeDefinitions())
    mockedFetchPairsByDefinition.mockImplementation((id) =>
      Promise.resolve(makePairs(id, id === 1 ? 2 : 0, 7)),
    )

    render(<CurrencyPairManagementPage />)
    await screen.findByRole('table')

    const activeRow = findRowByCodes('USD', 'JPY')
    await within(activeRow).findByText('2 / 7')
    const deleteBtn = within(activeRow).getByRole('button', { name: '刪除' })
    expect(deleteBtn).toBeDisabled()
    expect(deleteBtn).toHaveAttribute(
      'title',
      '需先於「品牌幣種對」頁面關閉所有品牌幣種對才能刪除',
    )

    const inactiveRow = findRowByCodes('USD', 'EUR')
    await within(inactiveRow).findByText('0 / 7')
    expect(
      within(inactiveRow).getByRole('button', { name: '刪除' }),
    ).not.toBeDisabled()
  })

  it('deletes a definition after confirmation when 啟用品牌數 is 0', async () => {
    mockedFetchDefinitions.mockResolvedValue(makeDefinitions())
    mockedFetchPairsByDefinition.mockImplementation((id) =>
      Promise.resolve(makePairs(id, id === 1 ? 2 : 0, 7)),
    )
    mockedDeleteDefinition.mockResolvedValue(undefined)

    render(<CurrencyPairManagementPage />)
    await screen.findByRole('table')

    const inactiveRow = findRowByCodes('USD', 'EUR')
    await within(inactiveRow).findByText('0 / 7')
    await userEvent.click(
      within(inactiveRow).getByRole('button', { name: '刪除' }),
    )

    await screen.findByText(/確定要刪除幣種對「USD\/EUR」嗎/)
    await userEvent.click(
      screen.getAllByRole('button', { name: '刪除' }).at(-1)!,
    )

    expect(mockedDeleteDefinition).toHaveBeenCalledWith(2)
    await screen.findByText('幣種對已刪除')
    expect(screen.queryByText('EUR')).not.toBeInTheDocument()
  })

  it('shows the response error message and refreshes the count on a 409 delete race', async () => {
    mockedFetchDefinitions.mockResolvedValue(makeDefinitions())
    mockedFetchPairsByDefinition
      .mockResolvedValueOnce(makePairs(1, 2, 7))
      .mockResolvedValueOnce(makePairs(2, 0, 7))
    mockedDeleteDefinition.mockRejectedValue(
      new ApiError(409, 'Active brand currency pairs exist'),
    )

    render(<CurrencyPairManagementPage />)
    await screen.findByRole('table')

    const inactiveRow = findRowByCodes('USD', 'EUR')
    await within(inactiveRow).findByText('0 / 7')

    mockedFetchPairsByDefinition.mockResolvedValueOnce(makePairs(2, 1, 7))

    await userEvent.click(
      within(inactiveRow).getByRole('button', { name: '刪除' }),
    )
    await userEvent.click(
      screen.getAllByRole('button', { name: '刪除' }).at(-1)!,
    )

    await screen.findByText('Active brand currency pairs exist')
    await waitFor(() => {
      expect(
        within(findRowByCodes('USD', 'EUR')).getByText('1 / 7'),
      ).toBeInTheDocument()
    })
  })
})
