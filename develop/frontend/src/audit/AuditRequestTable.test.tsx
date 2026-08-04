import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuditRequestTable } from './AuditRequestTable'
import type { AuditRequest } from './types'

const PENDING_REQUEST: AuditRequest = {
  id: 1,
  entityType: 'TEST_ENTITY',
  actionType: 'UPDATE',
  entityId: 3,
  status: 'PENDING',
  summary: 'PUG · USD/TWD',
  before: { rate: 1 },
  after: { rate: 2 },
  requestedBy: 'Alice',
  requestedAt: '2026-07-29T10:00:00',
  reviewedBy: null,
  reviewedAt: null,
  rejectReason: null,
  createdAt: '2026-07-29T10:00:00',
  updatedAt: '2026-07-29T10:00:00',
}

describe('AuditRequestTable', () => {
  it('shows a loading indicator', () => {
    render(<AuditRequestTable requests={[]} loading onView={vi.fn()} />)

    expect(screen.getByRole('status')).toHaveTextContent('載入中...')
  })

  it('shows an empty state when there are no requests', () => {
    render(<AuditRequestTable requests={[]} loading={false} onView={vi.fn()} />)

    expect(screen.getByText('目前沒有審核申請')).toBeInTheDocument()
  })

  it('renders 類型/摘要/申請人/申請時間/狀態 for each row', () => {
    render(<AuditRequestTable requests={[PENDING_REQUEST]} loading={false} onView={vi.fn()} />)

    expect(screen.getByText('修改')).toBeInTheDocument()
    expect(screen.getByText('PUG · USD/TWD')).toBeInTheDocument()
    expect(screen.getByText('Alice')).toBeInTheDocument()
    expect(screen.getByText('2026-07-29 10:00')).toBeInTheDocument()
    expect(screen.getByText('待審核')).toBeInTheDocument()
  })

  it('renders a dash for a null summary and requestedBy', () => {
    render(
      <AuditRequestTable
        requests={[{ ...PENDING_REQUEST, summary: null, requestedBy: null }]}
        loading={false}
        onView={vi.fn()}
      />,
    )

    expect(screen.getAllByText('—')).toHaveLength(2)
  })

  it('calls onView with the request when 查看 is clicked', async () => {
    const onView = vi.fn()
    render(<AuditRequestTable requests={[PENDING_REQUEST]} loading={false} onView={onView} />)

    await userEvent.click(screen.getByText('查看'))

    expect(onView).toHaveBeenCalledWith(PENDING_REQUEST)
  })

  it('renders action-type and status badges for APPROVED/REJECTED/CREATE/DELETE', () => {
    render(
      <AuditRequestTable
        requests={[
          { ...PENDING_REQUEST, id: 2, actionType: 'CREATE', status: 'APPROVED' },
          { ...PENDING_REQUEST, id: 3, actionType: 'DELETE', status: 'REJECTED' },
        ]}
        loading={false}
        onView={vi.fn()}
      />,
    )

    expect(screen.getByText('新增')).toBeInTheDocument()
    expect(screen.getByText('已核准')).toBeInTheDocument()
    expect(screen.getByText('刪除')).toBeInTheDocument()
    expect(screen.getByText('已拒絕')).toBeInTheDocument()
  })
})
