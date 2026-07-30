import { describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { SpreadGroupTable } from './SpreadGroupTable'
import type { SpreadGroup } from '../types/spread'

const GROUP: SpreadGroup = {
  id: 10,
  brandId: 1,
  brandCode: 'AU',
  name: 'Group A',
  depositSpread: 0.1,
  withdrawSpread: 0.2,
  members: [
    { currencyPairId: 3, baseCurrencyCode: 'USD', quoteCurrencyCode: 'JPY' },
    { currencyPairId: 4, baseCurrencyCode: 'USD', quoteCurrencyCode: 'EUR' },
  ],
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
}

describe('SpreadGroupTable', () => {
  it('shows the empty state when there are no groups', () => {
    render(<SpreadGroupTable groups={[]} pendingIds={new Set()} onEdit={vi.fn()} onDelete={vi.fn()} />)
    expect(screen.getByText('目前沒有點差群組')).toBeInTheDocument()
  })

  it('renders a group row with name, spreads, and member badges', () => {
    render(<SpreadGroupTable groups={[GROUP]} pendingIds={new Set()} onEdit={vi.fn()} onDelete={vi.fn()} />)

    expect(screen.getByText('Group A')).toBeInTheDocument()
    expect(screen.getByText('0.1')).toBeInTheDocument()
    expect(screen.getByText('0.2')).toBeInTheDocument()
    expect(screen.getByText('USD/JPY')).toBeInTheDocument()
    expect(screen.getByText('USD/EUR')).toBeInTheDocument()
  })

  it('calls onEdit/onDelete when the row buttons are clicked', async () => {
    const onEdit = vi.fn()
    const onDelete = vi.fn()
    render(<SpreadGroupTable groups={[GROUP]} pendingIds={new Set()} onEdit={onEdit} onDelete={onDelete} />)

    screen.getByText('編輯').click()
    expect(onEdit).toHaveBeenCalledWith(GROUP)

    screen.getByText('刪除').click()
    expect(onDelete).toHaveBeenCalledWith(GROUP)
  })

  it('marks a pending row with 審核中 and disables its action buttons', () => {
    render(<SpreadGroupTable groups={[GROUP]} pendingIds={new Set([10])} onEdit={vi.fn()} onDelete={vi.fn()} />)

    const row = screen.getByText('Group A').closest('tr')!
    expect(within(row).getByText('審核中')).toBeInTheDocument()
    expect(within(row).getByText('編輯')).toBeDisabled()
    expect(within(row).getByText('刪除')).toBeDisabled()
  })
})
