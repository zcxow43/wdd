import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CurrencyPairFormModal } from './CurrencyPairFormModal'
import { ApiError } from '../api/client'
import type { CurrencyPair } from '../types/currencyPair'
import type { Brand } from '../types/brand'
import type { Currency } from '../types/currency'

const AU: Brand = { id: 1, code: 'AU', name: 'AU', active: true, createdAt: '', updatedAt: '' }
const PUG: Brand = { id: 3, code: 'PUG', name: 'PUG', active: true, createdAt: '', updatedAt: '' }
const BRANDS = [AU, PUG]

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
const TWD: Currency = {
  id: 1,
  code: 'TWD',
  name: 'New Taiwan Dollar',
  nameZh: null,
  symbol: null,
  decimalPlaces: 0,
  createdAt: '',
  updatedAt: '',
}
const CURRENCIES = [USD, TWD]

const MANUAL_PAIR: CurrencyPair = {
  id: 10,
  brandId: 3,
  brandCode: 'PUG',
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

const AUTO_PAIR: CurrencyPair = {
  ...MANUAL_PAIR,
  id: 11,
  rate: null,
  rateType: 'AUTO',
}

function renderModal(overrides: Partial<Parameters<typeof CurrencyPairFormModal>[0]> = {}) {
  return render(
    <CurrencyPairFormModal
      initial={MANUAL_PAIR}
      brands={BRANDS}
      currencies={CURRENCIES}
      onClose={vi.fn()}
      onSubmit={vi.fn().mockResolvedValue(undefined)}
      {...overrides}
    />,
  )
}

describe('CurrencyPairFormModal', () => {
  it('renders a fixed 編輯幣種對 title (no create mode)', () => {
    renderModal()

    expect(screen.getByText('編輯幣種對')).toBeInTheDocument()
  })

  it('pre-fills all fields from the required initial prop', () => {
    renderModal()

    expect(screen.getByLabelText('品牌')).toHaveValue('3')
    expect(screen.getByLabelText('基準幣別')).toHaveValue('2')
    expect(screen.getByLabelText('對應幣別')).toHaveValue('1')
    expect(screen.getByLabelText('匯率類型')).toHaveValue('MANUAL')
    expect(screen.getByLabelText('匯率')).toHaveValue(32.5)
    expect(screen.getByLabelText('匯率')).not.toBeDisabled()
  })

  it('shows required-field errors when the selects are cleared', async () => {
    renderModal()

    await userEvent.selectOptions(screen.getByLabelText('品牌'), '')
    await userEvent.selectOptions(screen.getByLabelText('基準幣別'), '')
    await userEvent.selectOptions(screen.getByLabelText('對應幣別'), '')
    await userEvent.click(screen.getByText('儲存'))

    expect(await screen.findByText('品牌為必填')).toBeInTheDocument()
    expect(screen.getByText('基準幣別為必填')).toBeInTheDocument()
    expect(screen.getByText('對應幣別為必填')).toBeInTheDocument()
  })

  it('shows a live inline error and disables submit when base and quote are the same', async () => {
    renderModal()

    await userEvent.selectOptions(screen.getByLabelText('對應幣別'), '2')

    expect(screen.getByText('基準幣別與對應幣別不可相同')).toBeInTheDocument()
    expect(screen.getByText('儲存')).toBeDisabled()
  })

  it('submits a valid edit form with numeric ids and rate', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    renderModal({ onSubmit })

    await userEvent.clear(screen.getByLabelText('匯率'))
    await userEvent.type(screen.getByLabelText('匯率'), '35')
    await userEvent.click(screen.getByText('儲存'))

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        brandId: 3,
        baseCurrencyId: 2,
        quoteCurrencyId: 1,
        rateType: 'MANUAL',
        rate: 35,
        active: true,
      }),
    )
  })

  it('disables and clears the rate input when 自動 is selected, showing helper text instead of an error', async () => {
    renderModal()

    await userEvent.selectOptions(screen.getByLabelText('匯率類型'), 'AUTO')

    expect(screen.getByLabelText('匯率')).toBeDisabled()
    expect(screen.getByLabelText('匯率')).toHaveValue(null)
    expect(screen.getByText('系統將自動維護匯率')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('系統自動維護')).toBeInTheDocument()

    await userEvent.click(screen.getByText('儲存'))
    expect(screen.queryByText('匯率為必填，且須大於 0')).not.toBeInTheDocument()
  })

  it('re-enables and requires the rate when switching back to 手動', async () => {
    renderModal()

    await userEvent.selectOptions(screen.getByLabelText('匯率類型'), 'AUTO')
    await userEvent.selectOptions(screen.getByLabelText('匯率類型'), 'MANUAL')

    expect(screen.getByLabelText('匯率')).not.toBeDisabled()
    expect(screen.getByLabelText('匯率')).toHaveValue(null)

    await userEvent.click(screen.getByText('儲存'))
    expect(await screen.findByText('匯率為必填，且須大於 0')).toBeInTheDocument()
  })

  it('submits rate: null when 自動 is selected', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    renderModal({ onSubmit })

    await userEvent.selectOptions(screen.getByLabelText('匯率類型'), 'AUTO')
    await userEvent.click(screen.getByText('儲存'))

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        brandId: 3,
        baseCurrencyId: 2,
        quoteCurrencyId: 1,
        rateType: 'AUTO',
        rate: null,
        active: true,
      }),
    )
  })

  it('correctly reflects a loaded AUTO pair (rate null) as disabled/blank', () => {
    renderModal({ initial: AUTO_PAIR })

    expect(screen.getByLabelText('匯率類型')).toHaveValue('AUTO')
    expect(screen.getByLabelText('匯率')).toBeDisabled()
    expect(screen.getByLabelText('匯率')).toHaveValue(null)
  })

  it('shows the live-duplicate inline message on a 409 with the live-duplicate error text', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(
        new ApiError(409, { error: 'Currency pair already exists for this brand/base/quote combination' }),
      )
    renderModal({ onSubmit })

    await userEvent.click(screen.getByText('儲存'))

    expect(await screen.findByText('此品牌已存在相同的幣種對')).toBeInTheDocument()
  })

  it('shows the pending-duplicate fallback message on any other 409', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(409, { error: 'A pending audit request already exists for this entity' }))
    renderModal({ onSubmit })

    await userEvent.click(screen.getByText('儲存'))

    expect(await screen.findByText('此幣種對已有待審核的異動申請')).toBeInTheDocument()
  })
})
