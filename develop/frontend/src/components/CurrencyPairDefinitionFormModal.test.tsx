import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CurrencyPairDefinitionFormModal } from './CurrencyPairDefinitionFormModal'
import { ApiError } from '../api/client'
import type { CurrencyPairDefinition } from '../types/currencyPairDefinition'
import type { Currency } from '../types/currency'

const CURRENCIES: Currency[] = [
  {
    id: 1,
    code: 'TWD',
    name: 'New Taiwan Dollar',
    nameZh: '新台幣',
    symbol: 'NT$',
    decimalPlaces: 0,
    createdAt: '2025-01-01T00:00:00',
    updatedAt: '2025-01-01T00:00:00',
  },
  {
    id: 2,
    code: 'USD',
    name: 'United States Dollar',
    nameZh: '美元',
    symbol: '$',
    decimalPlaces: 2,
    createdAt: '2025-01-01T00:00:00',
    updatedAt: '2025-01-01T00:00:00',
  },
]

const EXISTING: CurrencyPairDefinition = {
  id: 1,
  baseCurrencyId: 2,
  baseCurrencyCode: 'USD',
  quoteCurrencyId: 1,
  quoteCurrencyCode: 'TWD',
  forwardPrecision: 2,
  reversePrecision: 5,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

describe('CurrencyPairDefinitionFormModal', () => {
  it('shows validation errors when required fields are missing in create mode', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(
      <CurrencyPairDefinitionFormModal mode="create" currencies={CURRENCIES} onSubmit={onSubmit} onClose={vi.fn()} />,
    )

    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('基準幣別為必填')).toBeInTheDocument()
    expect(screen.getByText('對應幣別為必填')).toBeInTheDocument()
    expect(screen.getByText('正向精度須為 0 到 8 之間的整數')).toBeInTheDocument()
    expect(screen.getByText('反向精度須為 0 到 8 之間的整數')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('shows an inline error and disables submit when base and quote currency are the same', async () => {
    const user = userEvent.setup()
    render(
      <CurrencyPairDefinitionFormModal mode="create" currencies={CURRENCIES} onSubmit={vi.fn()} onClose={vi.fn()} />,
    )

    await user.selectOptions(screen.getByLabelText('基準幣別'), '2')
    await user.selectOptions(screen.getByLabelText('對應幣別'), '2')

    expect(await screen.findByText('基準幣別與對應幣別不可相同')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '儲存' })).toBeDisabled()
  })

  it('submits a valid create form with numeric ids', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(
      <CurrencyPairDefinitionFormModal mode="create" currencies={CURRENCIES} onSubmit={onSubmit} onClose={vi.fn()} />,
    )

    await user.selectOptions(screen.getByLabelText('基準幣別'), '2')
    await user.selectOptions(screen.getByLabelText('對應幣別'), '1')
    await user.type(screen.getByLabelText('正向精度'), '2')
    await user.type(screen.getByLabelText('反向精度'), '5')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        baseCurrencyId: 2,
        quoteCurrencyId: 1,
        forwardPrecision: 2,
        reversePrecision: 5,
      }),
    )
  })

  it('pre-fills values in edit mode and disables the currency selects', async () => {
    render(
      <CurrencyPairDefinitionFormModal
        mode="edit"
        initial={EXISTING}
        currencies={CURRENCIES}
        onSubmit={vi.fn()}
        onClose={vi.fn()}
      />,
    )

    const baseSelect = screen.getByLabelText('基準幣別') as HTMLSelectElement
    const quoteSelect = screen.getByLabelText('對應幣別') as HTMLSelectElement
    expect(baseSelect.value).toBe('2')
    expect(quoteSelect.value).toBe('1')
    expect(baseSelect).toBeDisabled()
    expect(quoteSelect).toBeDisabled()
    expect((screen.getByLabelText('正向精度') as HTMLInputElement).value).toBe('2')
    expect((screen.getByLabelText('反向精度') as HTMLInputElement).value).toBe('5')
  })

  it('submits only precision fields in edit mode', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(
      <CurrencyPairDefinitionFormModal
        mode="edit"
        initial={EXISTING}
        currencies={CURRENCIES}
        onSubmit={onSubmit}
        onClose={vi.fn()}
      />,
    )

    const forwardInput = screen.getByLabelText('正向精度')
    await user.clear(forwardInput)
    await user.type(forwardInput, '3')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        forwardPrecision: 3,
        reversePrecision: 5,
      }),
    )
  })

  it('shows an inline duplicate-direction error under 對應幣別 when the API returns 409', async () => {
    const user = userEvent.setup()
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(409, { error: 'Reverse direction already exists' }, 'Conflict'))
    render(
      <CurrencyPairDefinitionFormModal mode="create" currencies={CURRENCIES} onSubmit={onSubmit} onClose={vi.fn()} />,
    )

    await user.selectOptions(screen.getByLabelText('基準幣別'), '2')
    await user.selectOptions(screen.getByLabelText('對應幣別'), '1')
    await user.type(screen.getByLabelText('正向精度'), '2')
    await user.type(screen.getByLabelText('反向精度'), '5')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('此幣種對（或其反向）已存在')).toBeInTheDocument()
  })

  it('shows a generic invalid-input message when the API returns 400', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockRejectedValue(new ApiError(400, { error: 'Invalid precision' }, 'Bad Request'))
    render(
      <CurrencyPairDefinitionFormModal mode="create" currencies={CURRENCIES} onSubmit={onSubmit} onClose={vi.fn()} />,
    )

    await user.selectOptions(screen.getByLabelText('基準幣別'), '2')
    await user.selectOptions(screen.getByLabelText('對應幣別'), '1')
    await user.type(screen.getByLabelText('正向精度'), '2')
    await user.type(screen.getByLabelText('反向精度'), '5')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('輸入資料有誤，請確認後再試')).toBeInTheDocument()
  })

  it('shows a network error message when the request fails to reach the server', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockRejectedValue(new Error('network down'))
    render(
      <CurrencyPairDefinitionFormModal mode="create" currencies={CURRENCIES} onSubmit={onSubmit} onClose={vi.fn()} />,
    )

    await user.selectOptions(screen.getByLabelText('基準幣別'), '2')
    await user.selectOptions(screen.getByLabelText('對應幣別'), '1')
    await user.type(screen.getByLabelText('正向精度'), '2')
    await user.type(screen.getByLabelText('反向精度'), '5')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
  })
})
