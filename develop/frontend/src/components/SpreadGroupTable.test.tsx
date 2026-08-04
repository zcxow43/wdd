import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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
  createdAt: '',
  updatedAt: '',
}

describe('SpreadGroupTable', () => {
  it('renders columns for each group, including comma-joined member badges', () => {
    render(
      <SpreadGroupTable groups={[GROUP]} pendingIds={new Set()} onEdit={vi.fn()} onDelete={vi.fn()} />,
    )

    expect(screen.getByText('Group A')).toBeInTheDocument()
    expect(screen.getByText('0.1')).toBeInTheDocument()
    expect(screen.getByText('0.2')).toBeInTheDocument()
    expect(screen.getByText('USD/JPY')).toBeInTheDocument()
    expect(screen.getByText('USD/EUR')).toBeInTheDocument()
  })

  it('shows the empty state when there are no groups', () => {
    render(<SpreadGroupTable groups={[]} pendingIds={new Set()} onEdit={vi.fn()} onDelete={vi.fn()} />)

    expect(screen.getByText('目前沒有點差群組')).toBeInTheDocument()
  })

  it('calls onEdit/onDelete when the row action buttons are clicked', async () => {
    const onEdit = vi.fn()
    const onDelete = vi.fn()
    render(<SpreadGroupTable groups={[GROUP]} pendingIds={new Set()} onEdit={onEdit} onDelete={onDelete} />)

    await userEvent.click(screen.getByText('編輯'))
    expect(onEdit).toHaveBeenCalledWith(GROUP)

    await userEvent.click(screen.getByText('刪除'))
    expect(onDelete).toHaveBeenCalledWith(GROUP)
  })

  it('shows a 審核中 badge and disables actions for a pending row', () => {
    render(
      <SpreadGroupTable groups={[GROUP]} pendingIds={new Set([10])} onEdit={vi.fn()} onDelete={vi.fn()} />,
    )

    expect(screen.getByText('審核中')).toBeInTheDocument()
    expect(screen.getByText('編輯')).toBeDisabled()
    expect(screen.getByText('刪除')).toBeDisabled()
  })

  it('does not badge/disable a row when the pending id belongs to a different group', () => {
    render(
      <SpreadGroupTable groups={[GROUP]} pendingIds={new Set([999])} onEdit={vi.fn()} onDelete={vi.fn()} />,
    )

    expect(screen.queryByText('審核中')).not.toBeInTheDocument()
    expect(screen.getByText('編輯')).not.toBeDisabled()
  })
})
