import { describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuditRequestTable } from './AuditRequestTable'
import type { AuditRequest } from './types'

const REQUEST: AuditRequest = {
  id: 1,
  entityType: 'CURRENCY_PAIR',
  actionType: 'CREATE',
  entityId: null,
  status: 'PENDING',
  summary: 'PUG · USD/TWD',
  before: null,
  after: { rate: 32.5 },
  requestedBy: 'Alice',
  requestedAt: '2026-07-29T10:00:00',
  reviewedBy: null,
  reviewedAt: null,
  rejectReason: null,
  createdAt: '2026-07-29T10:00:00',
  updatedAt: '2026-07-29T10:00:00',
}

describe('AuditRequestTable', () => {
  it('renders the empty state when there are no requests', () => {
    render(<AuditRequestTable requests={[]} onView={vi.fn()} />)

    expect(screen.getByText('目前沒有符合條件的審核申請')).toBeInTheDocument()
  })

  it('renders all columns for a request row', () => {
    render(<AuditRequestTable requests={[REQUEST]} onView={vi.fn()} />)

    expect(screen.getByText('新增')).toBeInTheDocument()
    expect(screen.getByText('PUG · USD/TWD')).toBeInTheDocument()
    expect(screen.getByText('Alice')).toBeInTheDocument()
    expect(screen.getByText('2026-07-29 10:00')).toBeInTheDocument()
    expect(screen.getByText('待審核')).toBeInTheDocument()
  })

  it('renders — for a null summary and requestedBy', () => {
    render(
      <AuditRequestTable
        requests={[{ ...REQUEST, summary: null, requestedBy: null }]}
        onView={vi.fn()}
      />,
    )

    expect(screen.getAllByText('—')).toHaveLength(2)
  })

  it('shows the correct action/status labels for UPDATE/APPROVED and DELETE/REJECTED', () => {
    render(
      <AuditRequestTable
        requests={[
          { ...REQUEST, id: 2, actionType: 'UPDATE', status: 'APPROVED' },
          { ...REQUEST, id: 3, actionType: 'DELETE', status: 'REJECTED' },
        ]}
        onView={vi.fn()}
      />,
    )

    expect(screen.getByText('修改')).toBeInTheDocument()
    expect(screen.getByText('已核准')).toBeInTheDocument()
    expect(screen.getByText('刪除')).toBeInTheDocument()
    expect(screen.getByText('已拒絕')).toBeInTheDocument()
  })

  it('calls onView with the clicked request', async () => {
    const user = userEvent.setup()
    const onView = vi.fn()
    render(<AuditRequestTable requests={[REQUEST]} onView={onView} />)

    const row = screen.getByText('PUG · USD/TWD').closest('tr')!
    await user.click(within(row).getByText('查看'))

    expect(onView).toHaveBeenCalledWith(REQUEST)
  })
})
