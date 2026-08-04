import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuditReviewModal } from './AuditReviewModal'
import { registerDiffRenderer } from './diffRegistry'
import type { AuditRequest } from './types'

const UPDATE_REQUEST: AuditRequest = {
  id: 1,
  entityType: 'TEST_ENTITY_MODAL',
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

describe('AuditReviewModal', () => {
  it('shows the action-type label in the header and renders the diff via renderAuditDiff', () => {
    render(
      <AuditReviewModal
        request={UPDATE_REQUEST}
        onClose={vi.fn()}
        onApprove={vi.fn()}
        onReject={vi.fn()}
      />,
    )

    expect(screen.getByText('審核異動申請 — 修改')).toBeInTheDocument()
    expect(screen.getByText('rate')).toBeInTheDocument()
    expect(screen.getByText('1')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
  })

  it('shows the CREATE placeholder instead of calling the renderer when before is null', () => {
    render(
      <AuditReviewModal
        request={{ ...UPDATE_REQUEST, actionType: 'CREATE', before: null }}
        onClose={vi.fn()}
        onApprove={vi.fn()}
        onReject={vi.fn()}
      />,
    )

    expect(screen.getByText('（新增，無先前資料）')).toBeInTheDocument()
    expect(screen.queryByText('rate')).not.toBeInTheDocument()
  })

  it('shows the DELETE placeholder instead of calling the renderer when after is null', () => {
    render(
      <AuditReviewModal
        request={{ ...UPDATE_REQUEST, actionType: 'DELETE', after: null }}
        onClose={vi.fn()}
        onApprove={vi.fn()}
        onReject={vi.fn()}
      />,
    )

    expect(screen.getByText('（將被刪除）')).toBeInTheDocument()
    expect(screen.queryByText('rate')).not.toBeInTheDocument()
  })

  it('hands before/after null directly to a registered renderer that opts into it, instead of the placeholder', () => {
    registerDiffRenderer('TEST_ENTITY_NULL_AWARE', (before, after) => (
      <div data-testid="null-aware-diff">
        before: {before === null ? 'NULL' : 'SET'} / after: {after === null ? 'NULL' : 'SET'}
      </div>
    ))

    render(
      <AuditReviewModal
        request={{ ...UPDATE_REQUEST, entityType: 'TEST_ENTITY_NULL_AWARE', actionType: 'CREATE', before: null }}
        onClose={vi.fn()}
        onApprove={vi.fn()}
        onReject={vi.fn()}
      />,
    )

    expect(screen.getByTestId('null-aware-diff')).toHaveTextContent('before: NULL / after: SET')
    expect(screen.queryByText('（新增，無先前資料）')).not.toBeInTheDocument()
  })

  it('shows requester metadata', () => {
    render(
      <AuditReviewModal
        request={UPDATE_REQUEST}
        onClose={vi.fn()}
        onApprove={vi.fn()}
        onReject={vi.fn()}
      />,
    )

    expect(screen.getByText('申請人: Alice')).toBeInTheDocument()
    expect(screen.getByText('申請時間: 2026-07-29 10:00')).toBeInTheDocument()
  })

  it('hides approve/reject and shows reviewer metadata for an already-reviewed request', () => {
    render(
      <AuditReviewModal
        request={{
          ...UPDATE_REQUEST,
          status: 'REJECTED',
          reviewedBy: 'Bob',
          reviewedAt: '2026-07-30T09:00:00',
          rejectReason: '匯率過高',
        }}
        onClose={vi.fn()}
        onApprove={vi.fn()}
        onReject={vi.fn()}
      />,
    )

    expect(screen.queryByText('核准')).not.toBeInTheDocument()
    expect(screen.queryByText('拒絕')).not.toBeInTheDocument()
    expect(screen.getByText('審核人: Bob')).toBeInTheDocument()
    expect(screen.getByText('審核時間: 2026-07-30 09:00')).toBeInTheDocument()
    expect(screen.getByText('拒絕原因: 匯率過高')).toBeInTheDocument()
  })

  it('approves after a confirm dialog and calls onApprove', async () => {
    const onApprove = vi.fn().mockResolvedValue(undefined)
    render(
      <AuditReviewModal
        request={UPDATE_REQUEST}
        onClose={vi.fn()}
        onApprove={onApprove}
        onReject={vi.fn()}
      />,
    )

    await userEvent.click(screen.getByText('核准'))
    expect(screen.getByText('確定要核准此異動申請嗎？')).toBeInTheDocument()

    await userEvent.click(screen.getByText('確定'))

    await waitFor(() => expect(onApprove).toHaveBeenCalledWith(UPDATE_REQUEST))
  })

  it('shows an inline error and keeps the modal open when approve fails', async () => {
    const onApprove = vi.fn().mockRejectedValue(new Error('Currency pair is inactive'))
    render(
      <AuditReviewModal
        request={UPDATE_REQUEST}
        onClose={vi.fn()}
        onApprove={onApprove}
        onReject={vi.fn()}
      />,
    )

    await userEvent.click(screen.getByText('核准'))
    await userEvent.click(screen.getByText('確定'))

    expect(await screen.findByText('Currency pair is inactive')).toBeInTheDocument()
    expect(screen.getByText('核准')).toBeInTheDocument()
  })

  it('requires a non-empty reject reason before calling onReject', async () => {
    const onReject = vi.fn()
    render(
      <AuditReviewModal
        request={UPDATE_REQUEST}
        onClose={vi.fn()}
        onApprove={vi.fn()}
        onReject={onReject}
      />,
    )

    await userEvent.click(screen.getByText('拒絕'))
    await userEvent.click(screen.getByText('確認拒絕'))

    expect(screen.getByText('請輸入拒絕原因')).toBeInTheDocument()
    expect(onReject).not.toHaveBeenCalled()
  })

  it('rejects with a trimmed reason and calls onReject', async () => {
    const onReject = vi.fn().mockResolvedValue(undefined)
    render(
      <AuditReviewModal
        request={UPDATE_REQUEST}
        onClose={vi.fn()}
        onApprove={vi.fn()}
        onReject={onReject}
      />,
    )

    await userEvent.click(screen.getByText('拒絕'))
    await userEvent.type(screen.getByLabelText('拒絕原因'), '  匯率過高，請重新確認  ')
    await userEvent.click(screen.getByText('確認拒絕'))

    await waitFor(() =>
      expect(onReject).toHaveBeenCalledWith(UPDATE_REQUEST, '匯率過高，請重新確認'),
    )
  })

  it('shows an inline error from a failed reject without closing', async () => {
    const onReject = vi.fn().mockRejectedValue(new Error('請輸入拒絕原因'))
    render(
      <AuditReviewModal
        request={UPDATE_REQUEST}
        onClose={vi.fn()}
        onApprove={vi.fn()}
        onReject={onReject}
      />,
    )

    await userEvent.click(screen.getByText('拒絕'))
    await userEvent.type(screen.getByLabelText('拒絕原因'), '原因')
    await userEvent.click(screen.getByText('確認拒絕'))

    expect(await screen.findByText('請輸入拒絕原因')).toBeInTheDocument()
  })

  it('cancels the reject flow and returns to the approve/reject buttons', async () => {
    render(
      <AuditReviewModal
        request={UPDATE_REQUEST}
        onClose={vi.fn()}
        onApprove={vi.fn()}
        onReject={vi.fn()}
      />,
    )

    await userEvent.click(screen.getByText('拒絕'))
    expect(screen.getByLabelText('拒絕原因')).toBeInTheDocument()

    await userEvent.click(screen.getByText('取消'))

    expect(screen.queryByLabelText('拒絕原因')).not.toBeInTheDocument()
    expect(screen.getByText('核准')).toBeInTheDocument()
  })
})
