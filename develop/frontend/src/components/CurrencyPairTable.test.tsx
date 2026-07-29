import { describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CurrencyPairTable } from './CurrencyPairTable'
import type { CurrencyPair } from '../types/currencyPair'

const PAIRS: CurrencyPair[] = [
  {
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
  },
  {
    id: 2,
    brandId: 2,
    brandCode: 'MONETA',
    baseCurrencyId: 2,
    baseCurrencyCode: 'USD',
    quoteCurrencyId: 3,
    quoteCurrencyCode: 'JPY',
    rate: null,
    rateType: 'AUTO',
    active: false,
    createdAt: '2025-01-01T00:00:00',
    updatedAt: '2025-01-01T00:00:00',
  },
]

describe('CurrencyPairTable', () => {
  it('renders all columns for each currency pair row', () => {
    render(<CurrencyPairTable pairs={PAIRS} pendingIds={new Set()} onEdit={vi.fn()} onDelete={vi.fn()} />)

    expect(screen.getByText('AU')).toBeInTheDocument()
    expect(screen.getByText('MONETA')).toBeInTheDocument()
    expect(screen.getAllByText('USD')).toHaveLength(2)
    expect(screen.getByText('TWD')).toBeInTheDocument()
    expect(screen.getByText('JPY')).toBeInTheDocument()
    expect(screen.getByText('32.5')).toBeInTheDocument()
    expect(screen.getByText('—')).toBeInTheDocument()
    expect(screen.getByText('手動')).toBeInTheDocument()
    expect(screen.getByText('自動')).toBeInTheDocument()
  })

  it('renders the empty state when there are no pairs', () => {
    render(<CurrencyPairTable pairs={[]} pendingIds={new Set()} onEdit={vi.fn()} onDelete={vi.fn()} />)

    expect(screen.getByText('目前沒有符合條件的幣種對')).toBeInTheDocument()
  })

  it('calls onEdit and onDelete with the clicked pair', async () => {
    const user = userEvent.setup()
    const onEdit = vi.fn()
    const onDelete = vi.fn()
    render(<CurrencyPairTable pairs={PAIRS} pendingIds={new Set()} onEdit={onEdit} onDelete={onDelete} />)

    const auRow = screen.getByText('AU').closest('tr')!
    await user.click(within(auRow).getByText('Edit'))
    expect(onEdit).toHaveBeenCalledWith(PAIRS[0])

    await user.click(within(auRow).getByText('Delete'))
    expect(onDelete).toHaveBeenCalledWith(PAIRS[0])
  })

  it('renders — in the rate column when rate is null (AUTO pairs)', () => {
    const autoPair: CurrencyPair = {
      id: 3,
      brandId: 1,
      brandCode: 'TEST',
      baseCurrencyId: 1,
      baseCurrencyCode: 'EUR',
      quoteCurrencyId: 2,
      quoteCurrencyCode: 'GBP',
      rate: null,
      rateType: 'AUTO',
      active: true,
      createdAt: '2025-01-01T00:00:00',
      updatedAt: '2025-01-01T00:00:00',
    }
    render(<CurrencyPairTable pairs={[autoPair]} pendingIds={new Set()} onEdit={vi.fn()} onDelete={vi.fn()} />)

    expect(screen.getByText('—')).toBeInTheDocument()
    expect(screen.getByText('自動')).toBeInTheDocument()
  })

  it('shows a 審核中 badge and disables Edit/Delete for a pair with a pending request', async () => {
    const user = userEvent.setup()
    const onEdit = vi.fn()
    const onDelete = vi.fn()
    render(<CurrencyPairTable pairs={PAIRS} pendingIds={new Set([1])} onEdit={onEdit} onDelete={onDelete} />)

    const auRow = screen.getByText('AU').closest('tr')!
    expect(within(auRow).getByText('審核中')).toBeInTheDocument()
    expect(within(auRow).getByText('Edit')).toBeDisabled()
    expect(within(auRow).getByText('Delete')).toBeDisabled()

    await user.click(within(auRow).getByText('Edit'))
    expect(onEdit).not.toHaveBeenCalled()

    const monetaRow = screen.getByText('MONETA').closest('tr')!
    expect(within(monetaRow).queryByText('審核中')).not.toBeInTheDocument()
    expect(within(monetaRow).getByText('Edit')).not.toBeDisabled()
  })
})
