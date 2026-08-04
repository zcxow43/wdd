import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CurrencyPairDefinitionFormModal } from './CurrencyPairDefinitionFormModal'
import { ApiError } from '../api/client'
import type { CurrencyPairDefinition } from '../types/currencyPairDefinition'
import type { Currency } from '../types/currency'

const USD: Currency = {
  id: 2,
  code: 'USD',
  name: 'US Dollar',
  nameZh: null,
  symbol: null,
  decimalPlaces: 2,
  createdAt: '',
  updatedAt: '',
}
const JPY: Currency = {
  id: 3,
  code: 'JPY',
  name: 'Japanese Yen',
  nameZh: null,
  symbol: null,
  decimalPlaces: 0,
  createdAt: '',
  updatedAt: '',
}
const CURRENCIES = [USD, JPY]

const USD_JPY: CurrencyPairDefinition = {
  id: 1,
  baseCurrencyId: 2,
  baseCurrencyCode: 'USD',
  quoteCurrencyId: 3,
  quoteCurrencyCode: 'JPY',
  forwardPrecision: 2,
  reversePrecision: 5,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

describe('CurrencyPairDefinitionFormModal', () => {
  it('renders a 新增幣種對主檔 title in create mode with empty fields', () => {
    render(
      <CurrencyPairDefinitionFormModal
        mode="create"
        currencies={CURRENCIES}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />,
    )

    expect(screen.getByText('新增幣種對主檔')).toBeInTheDocument()
    expect(screen.getByLabelText('基準幣別')).toHaveValue('')
    expect(screen.getByLabelText('對應幣別')).toHaveValue('')
    expect(screen.getByLabelText('基準幣別')).not.toBeDisabled()
    expect(screen.getByLabelText('對應幣別')).not.toBeDisabled()
  })

  it('renders a 編輯幣種對主檔 title in edit mode with base/quote disabled and pre-filled', () => {
    render(
      <CurrencyPairDefinitionFormModal
        mode="edit"
        initial={USD_JPY}
        currencies={CURRENCIES}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />,
    )

    expect(screen.getByText('編輯幣種對主檔')).toBeInTheDocument()
    expect(screen.getByLabelText('基準幣別')).toHaveValue('2')
    expect(screen.getByLabelText('基準幣別')).toBeDisabled()
    expect(screen.getByLabelText('對應幣別')).toHaveValue('3')
    expect(screen.getByLabelText('對應幣別')).toBeDisabled()
    expect(screen.getByLabelText('正向精度')).toHaveValue(2)
    expect(screen.getByLabelText('反向精度')).toHaveValue(5)
  })

  it('shows required-field errors when submitted empty in create mode', async () => {
    render(
      <CurrencyPairDefinitionFormModal
        mode="create"
        currencies={CURRENCIES}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />,
    )

    await userEvent.click(screen.getByText('儲存'))

    expect(await screen.findByText('基準幣別為必填')).toBeInTheDocument()
    expect(screen.getByText('對應幣別為必填')).toBeInTheDocument()
    expect(screen.getByText('正向精度為必填')).toBeInTheDocument()
    expect(screen.getByText('反向精度為必填')).toBeInTheDocument()
  })

  it('shows a live inline error and disables submit when base and quote are the same', async () => {
    render(
      <CurrencyPairDefinitionFormModal
        mode="create"
        currencies={CURRENCIES}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />,
    )

    await userEvent.selectOptions(screen.getByLabelText('基準幣別'), '2')
    await userEvent.selectOptions(screen.getByLabelText('對應幣別'), '2')

    expect(screen.getByText('基準幣別與對應幣別不可相同')).toBeInTheDocument()
    expect(screen.getByText('儲存')).toBeDisabled()
  })

  it('rejects out-of-range precision values', async () => {
    render(
      <CurrencyPairDefinitionFormModal
        mode="create"
        currencies={CURRENCIES}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />,
    )

    await userEvent.selectOptions(screen.getByLabelText('基準幣別'), '2')
    await userEvent.selectOptions(screen.getByLabelText('對應幣別'), '3')
    await userEvent.type(screen.getByLabelText('正向精度'), '9')
    await userEvent.type(screen.getByLabelText('反向精度'), '5')
    await userEvent.click(screen.getByText('儲存'))

    expect(await screen.findByText('正向精度須為 0 到 8 的整數')).toBeInTheDocument()
  })

  it('submits a valid create form with numeric ids and precisions', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(
      <CurrencyPairDefinitionFormModal
        mode="create"
        currencies={CURRENCIES}
        onClose={vi.fn()}
        onSubmit={onSubmit}
      />,
    )

    await userEvent.selectOptions(screen.getByLabelText('基準幣別'), '2')
    await userEvent.selectOptions(screen.getByLabelText('對應幣別'), '3')
    await userEvent.type(screen.getByLabelText('正向精度'), '2')
    await userEvent.type(screen.getByLabelText('反向精度'), '5')
    await userEvent.click(screen.getByText('儲存'))

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        baseCurrencyId: 2,
        quoteCurrencyId: 3,
        forwardPrecision: 2,
        reversePrecision: 5,
      }),
    )
  })

  it('submits only precision fields (plus the unchanged base/quote) in edit mode', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(
      <CurrencyPairDefinitionFormModal
        mode="edit"
        initial={USD_JPY}
        currencies={CURRENCIES}
        onClose={vi.fn()}
        onSubmit={onSubmit}
      />,
    )

    await userEvent.clear(screen.getByLabelText('正向精度'))
    await userEvent.type(screen.getByLabelText('正向精度'), '4')
    await userEvent.click(screen.getByText('儲存'))

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        baseCurrencyId: 2,
        quoteCurrencyId: 3,
        forwardPrecision: 4,
        reversePrecision: 5,
      }),
    )
  })

  it('shows an inline "此幣種對（或其反向）已存在" error under 對應幣別 on a 409, and keeps the modal open', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new ApiError(409, { error: 'Reverse direction already exists' }))
    render(
      <CurrencyPairDefinitionFormModal
        mode="create"
        currencies={CURRENCIES}
        onClose={vi.fn()}
        onSubmit={onSubmit}
      />,
    )

    await userEvent.selectOptions(screen.getByLabelText('基準幣別'), '2')
    await userEvent.selectOptions(screen.getByLabelText('對應幣別'), '3')
    await userEvent.type(screen.getByLabelText('正向精度'), '2')
    await userEvent.type(screen.getByLabelText('反向精度'), '5')
    await userEvent.click(screen.getByText('儲存'))

    expect(await screen.findByText('此幣種對（或其反向）已存在')).toBeInTheDocument()
    expect(screen.getByText('新增幣種對主檔')).toBeInTheDocument()
  })

  it('shows a generic inline error on a 400, and keeps the modal open', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new ApiError(400, { error: 'Invalid precision' }))
    render(
      <CurrencyPairDefinitionFormModal
        mode="create"
        currencies={CURRENCIES}
        onClose={vi.fn()}
        onSubmit={onSubmit}
      />,
    )

    await userEvent.selectOptions(screen.getByLabelText('基準幣別'), '2')
    await userEvent.selectOptions(screen.getByLabelText('對應幣別'), '3')
    await userEvent.type(screen.getByLabelText('正向精度'), '2')
    await userEvent.type(screen.getByLabelText('反向精度'), '5')
    await userEvent.click(screen.getByText('儲存'))

    expect(await screen.findByText('輸入資料有誤，請確認後再試')).toBeInTheDocument()
    expect(screen.getByText('新增幣種對主檔')).toBeInTheDocument()
  })
})
