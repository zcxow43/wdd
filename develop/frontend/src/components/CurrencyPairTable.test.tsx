import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CurrencyPairTable } from './CurrencyPairTable'
import type { CurrencyPair } from '../types/currencyPair'

const MANUAL_PAIR: CurrencyPair = {
  id: 1,
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
  id: 2,
  brandId: 1,
  brandCode: 'AU',
  baseCurrencyId: 2,
  baseCurrencyCode: 'USD',
  quoteCurrencyId: 3,
  quoteCurrencyCode: 'JPY',
  rate: null,
  rateType: 'AUTO',
  active: false,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

describe('CurrencyPairTable', () => {
  it('renders all columns including brand, base/quote codes, rate, rate type, and active dot', () => {
    render(
      <CurrencyPairTable
        pairs={[MANUAL_PAIR]}
        loading={false}
        pendingIds={new Set()}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByText('品牌')).toBeInTheDocument()
    expect(screen.getByText('基準幣別')).toBeInTheDocument()
    expect(screen.getByText('對應幣別')).toBeInTheDocument()
    expect(screen.getByText('匯率')).toBeInTheDocument()
    expect(screen.getByText('匯率類型')).toBeInTheDocument()
    expect(screen.getByText('狀態')).toBeInTheDocument()

    expect(screen.getByText('PUG')).toBeInTheDocument()
    expect(screen.getByText('USD')).toBeInTheDocument()
    expect(screen.getByText('TWD')).toBeInTheDocument()
    expect(screen.getByText('32.5')).toBeInTheDocument()
    expect(screen.getByText('手動')).toBeInTheDocument()
    expect(screen.getByText('啟用')).toBeInTheDocument()
  })

  it('renders — in the 匯率 column for a pair with rate: null (AUTO)', () => {
    render(
      <CurrencyPairTable
        pairs={[AUTO_PAIR]}
        loading={false}
        pendingIds={new Set()}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByText('—')).toBeInTheDocument()
    expect(screen.getByText('自動')).toBeInTheDocument()
    expect(screen.getByText('停用')).toBeInTheDocument()
  })

  it('renders an empty state when there are no pairs', () => {
    render(
      <CurrencyPairTable pairs={[]} loading={false} pendingIds={new Set()} onEdit={vi.fn()} onDelete={vi.fn()} />,
    )

    expect(screen.getByText('目前沒有幣種對資料')).toBeInTheDocument()
  })

  it('renders a loading state', () => {
    render(
      <CurrencyPairTable pairs={[]} loading={true} pendingIds={new Set()} onEdit={vi.fn()} onDelete={vi.fn()} />,
    )

    expect(screen.getByText('載入中...')).toBeInTheDocument()
  })

  it('calls onEdit/onDelete when the row action buttons are clicked', async () => {
    const onEdit = vi.fn()
    const onDelete = vi.fn()
    render(
      <CurrencyPairTable
        pairs={[MANUAL_PAIR]}
        loading={false}
        pendingIds={new Set()}
        onEdit={onEdit}
        onDelete={onDelete}
      />,
    )

    await userEvent.click(screen.getByText('編輯'))
    expect(onEdit).toHaveBeenCalledWith(MANUAL_PAIR)

    await userEvent.click(screen.getByText('刪除'))
    expect(onDelete).toHaveBeenCalledWith(MANUAL_PAIR)
  })

  it('shows a 審核中 badge and disables Edit/Delete for a row whose id is in pendingIds', () => {
    render(
      <CurrencyPairTable
        pairs={[MANUAL_PAIR]}
        loading={false}
        pendingIds={new Set([1])}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByText('審核中')).toBeInTheDocument()
    expect(screen.getByText('編輯')).toBeDisabled()
    expect(screen.getByText('刪除')).toBeDisabled()
  })

  it('does not badge/disable a different pair whose id is not in pendingIds', () => {
    render(
      <CurrencyPairTable
        pairs={[MANUAL_PAIR, AUTO_PAIR]}
        loading={false}
        pendingIds={new Set([2])}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getAllByText('審核中')).toHaveLength(1)
    const editButtons = screen.getAllByText('編輯')
    expect(editButtons[0]).not.toBeDisabled()
    expect(editButtons[1]).toBeDisabled()
  })
})
