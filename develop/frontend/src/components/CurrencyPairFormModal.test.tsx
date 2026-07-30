import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CurrencyPairFormModal } from './CurrencyPairFormModal'
import { ApiError } from '../api/client'
import type { CurrencyPair } from '../types/currencyPair'
import type { Brand } from '../types/brand'
import type { Currency } from '../types/currency'

const BRANDS: Brand[] = [
  { id: 1, code: 'AU', name: 'AU', active: true, createdAt: '2025-01-01T00:00:00', updatedAt: '2025-01-01T00:00:00' },
  {
    id: 2,
    code: 'MONETA',
    name: 'MONETA',
    active: true,
    createdAt: '2025-01-01T00:00:00',
    updatedAt: '2025-01-01T00:00:00',
  },
]

const CURRENCIES: Currency[] = [
  {
    id: 1,
    code: 'TWD',
    name: 'New Taiwan Dollar',
    nameZh: '新台幣',
    symbol: 'NT$',
    decimalPlaces: 0,
    active: true,
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
    active: true,
    createdAt: '2025-01-01T00:00:00',
    updatedAt: '2025-01-01T00:00:00',
  },
]

const EXISTING: CurrencyPair = {
  id: 1,
  brandId: 1,
  brandCode: 'AU',
  baseCurrencyId: 2,
  baseCurrencyCode: 'USD',
  quoteCurrencyId: 1,
  quoteCurrencyCode: 'TWD',
  rate: 32.5,
  rateType: 'MANUAL',
  active: true,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

describe('CurrencyPairFormModal', () => {
  it('shows validation errors when required fields are cleared', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(
      <CurrencyPairFormModal initial={EXISTING} brands={BRANDS} currencies={CURRENCIES} onSubmit={onSubmit} onClose={vi.fn()} />,
    )

    await user.selectOptions(screen.getByLabelText('品牌'), '')
    await user.selectOptions(screen.getByLabelText('基準幣別'), '')
    await user.selectOptions(screen.getByLabelText('對應幣別'), '')
    await user.clear(screen.getByLabelText('匯率'))
    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('品牌為必填')).toBeInTheDocument()
    expect(screen.getByText('基準幣別為必填')).toBeInTheDocument()
    expect(screen.getByText('對應幣別為必填')).toBeInTheDocument()
    expect(screen.getByText('匯率為必填，且須大於 0')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('shows an inline error and disables submit when base and quote currency are the same', async () => {
    const user = userEvent.setup()
    render(
      <CurrencyPairFormModal initial={EXISTING} brands={BRANDS} currencies={CURRENCIES} onSubmit={vi.fn()} onClose={vi.fn()} />,
    )

    await user.selectOptions(screen.getByLabelText('對應幣別'), '2')

    expect(await screen.findByText('基準幣別與對應幣別不可相同')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '儲存' })).toBeDisabled()
  })

  it('submits a valid edit form with numeric ids', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(
      <CurrencyPairFormModal initial={EXISTING} brands={BRANDS} currencies={CURRENCIES} onSubmit={onSubmit} onClose={vi.fn()} />,
    )

    const rateInput = screen.getByLabelText('匯率')
    await user.clear(rateInput)
    await user.type(rateInput, '33')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        brandId: 1,
        baseCurrencyId: 2,
        quoteCurrencyId: 1,
        rate: 33,
        rateType: 'MANUAL',
        active: true,
      }),
    )
  })

  it('pre-fills values in edit mode and shows the helper text for AUTO rate type', async () => {
    render(
      <CurrencyPairFormModal
        initial={EXISTING}
        brands={BRANDS}
        currencies={CURRENCIES}
        onSubmit={vi.fn()}
        onClose={vi.fn()}
      />,
    )

    expect((screen.getByLabelText('品牌') as HTMLSelectElement).value).toBe('1')
    expect((screen.getByLabelText('基準幣別') as HTMLSelectElement).value).toBe('2')
    expect((screen.getByLabelText('對應幣別') as HTMLSelectElement).value).toBe('1')
    expect((screen.getByLabelText('匯率') as HTMLInputElement).value).toBe('32.5')

    const user = userEvent.setup()
    await user.selectOptions(screen.getByLabelText('匯率類型'), 'AUTO')
    expect(await screen.findByText('系統將自動維護匯率')).toBeInTheDocument()
    expect(screen.getByLabelText('匯率')).toBeDisabled()
    expect((screen.getByLabelText('匯率') as HTMLInputElement).value).toBe('')
  })

  it('shows an inline conflict message when the API returns 409', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockRejectedValue(new ApiError(409, { error: 'Currency pair already exists for this brand' }, 'Conflict'))
    render(
      <CurrencyPairFormModal initial={EXISTING} brands={BRANDS} currencies={CURRENCIES} onSubmit={onSubmit} onClose={vi.fn()} />,
    )

    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('此品牌已存在相同的幣種對')).toBeInTheDocument()
  })

  it('shows the pending-duplicate conflict message for any other 409 body', async () => {
    const user = userEvent.setup()
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(409, { error: 'A pending audit request already exists for this entity' }, 'Conflict'))
    render(
      <CurrencyPairFormModal initial={EXISTING} brands={BRANDS} currencies={CURRENCIES} onSubmit={onSubmit} onClose={vi.fn()} />,
    )

    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('此幣種對已有待審核的異動申請')).toBeInTheDocument()
  })

  it('shows a network error message when the request fails to reach the server', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockRejectedValue(new Error('network down'))
    render(
      <CurrencyPairFormModal initial={EXISTING} brands={BRANDS} currencies={CURRENCIES} onSubmit={onSubmit} onClose={vi.fn()} />,
    )

    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
  })

  it('disables and clears the rate input when AUTO is selected', async () => {
    const user = userEvent.setup()
    render(
      <CurrencyPairFormModal initial={EXISTING} brands={BRANDS} currencies={CURRENCIES} onSubmit={vi.fn()} onClose={vi.fn()} />,
    )

    const rateInput = screen.getByLabelText('匯率') as HTMLInputElement
    expect(rateInput).not.toBeDisabled()
    expect(rateInput.value).toBe('32.5')

    await user.selectOptions(screen.getByLabelText('匯率類型'), 'AUTO')
    expect(rateInput).toBeDisabled()
    expect(rateInput.value).toBe('')
    expect(screen.getByText('系統將自動維護匯率')).toBeInTheDocument()
  })

  it('re-enables and requires rate when switching from AUTO to MANUAL', async () => {
    const user = userEvent.setup()
    render(
      <CurrencyPairFormModal initial={EXISTING} brands={BRANDS} currencies={CURRENCIES} onSubmit={vi.fn()} onClose={vi.fn()} />,
    )

    await user.selectOptions(screen.getByLabelText('匯率類型'), 'AUTO')
    const rateInput = screen.getByLabelText('匯率') as HTMLInputElement
    expect(rateInput).toBeDisabled()

    await user.selectOptions(screen.getByLabelText('匯率類型'), 'MANUAL')
    expect(rateInput).not.toBeDisabled()
    expect(rateInput.value).toBe('')

    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('匯率為必填，且須大於 0')).toBeInTheDocument()
  })

  it('does not show rate validation error when AUTO is selected and rate is blank', async () => {
    const user = userEvent.setup()
    render(
      <CurrencyPairFormModal initial={EXISTING} brands={BRANDS} currencies={CURRENCIES} onSubmit={vi.fn()} onClose={vi.fn()} />,
    )

    await user.selectOptions(screen.getByLabelText('匯率類型'), 'AUTO')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    await waitFor(() => expect(screen.queryByText('匯率為必填，且須大於 0')).not.toBeInTheDocument())
  })

  it('submits rate: null when AUTO is selected', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(
      <CurrencyPairFormModal initial={EXISTING} brands={BRANDS} currencies={CURRENCIES} onSubmit={onSubmit} onClose={vi.fn()} />,
    )

    await user.selectOptions(screen.getByLabelText('匯率類型'), 'AUTO')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        brandId: 1,
        baseCurrencyId: 2,
        quoteCurrencyId: 1,
        rate: null,
        rateType: 'AUTO',
        active: true,
      }),
    )
  })

  it('correctly reflects a loaded AUTO pair with null rate as disabled/blank', () => {
    const autoPair: CurrencyPair = {
      ...EXISTING,
      rate: null,
      rateType: 'AUTO',
    }
    render(
      <CurrencyPairFormModal
        initial={autoPair}
        brands={BRANDS}
        currencies={CURRENCIES}
        onSubmit={vi.fn()}
        onClose={vi.fn()}
      />,
    )

    const rateInput = screen.getByLabelText('匯率') as HTMLInputElement
    expect(rateInput).toBeDisabled()
    expect(rateInput.value).toBe('')
    expect(screen.getByText('系統將自動維護匯率')).toBeInTheDocument()
  })
})
