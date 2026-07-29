import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuditReviewModal } from './AuditReviewModal'
import { ApiError } from '../api/client'
import { registerDiffRenderer } from './diffRegistry'
import type { AuditRequest } from './types'

const PENDING_UPDATE: AuditRequest = {
  id: 1,
  entityType: 'SOME_UNKNOWN_ENTITY_FOR_MODAL_TEST',
  actionType: 'UPDATE',
  entityId: 5,
  status: 'PENDING',
  summary: 'PUG · USD/TWD',
  before: { rate: 30 },
  after: { rate: 32 },
  requestedBy: 'Alice',
  requestedAt: '2026-07-29T10:00:00',
  reviewedBy: null,
  reviewedAt: null,
  rejectReason: null,
  createdAt: '2026-07-29T10:00:00',
  updatedAt: '2026-07-29T10:00:00',
}

describe('AuditReviewModal', () => {
  it('renders the generic fallback diff when entityType has no registered renderer', () => {
    render(
      <AuditReviewModal request={PENDING_UPDATE} onApprove={vi.fn()} onReject={vi.fn()} onClose={vi.fn()} />,
    )

    expect(screen.getByText('rate')).toBeInTheDocument()
    expect(screen.getByText('30')).toBeInTheDocument()
    expect(screen.getByText('32')).toBeInTheDocument()
  })

  it('uses a registered renderer for its entityType', () => {
    registerDiffRenderer('MODAL_TEST_ENTITY', () => <div data-testid="custom">custom diff</div>)
    render(
      <AuditReviewModal
        request={{ ...PENDING_UPDATE, entityType: 'MODAL_TEST_ENTITY' }}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        onClose={vi.fn()}
      />,
    )

    expect(screen.getByTestId('custom')).toBeInTheDocument()
  })

  it('shows a placeholder instead of the diff for a CREATE request (before === null)', () => {
    render(
      <AuditReviewModal
        request={{ ...PENDING_UPDATE, actionType: 'CREATE', before: null }}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        onClose={vi.fn()}
      />,
    )

    expect(screen.getByText('（新增，無先前資料）')).toBeInTheDocument()
    expect(screen.queryByText('rate')).not.toBeInTheDocument()
  })

  it('shows a placeholder instead of the diff for a DELETE request (after === null)', () => {
    render(
      <AuditReviewModal
        request={{ ...PENDING_UPDATE, actionType: 'DELETE', after: null }}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        onClose={vi.fn()}
      />,
    )

    expect(screen.getByText('（將被刪除）')).toBeInTheDocument()
  })

  it('passes a null before/after straight to a registered renderer instead of showing the generic placeholder', () => {
    registerDiffRenderer('MODAL_NULL_TEST_ENTITY', (before, after) => (
      <div data-testid="custom-null-aware">
        before:{before === null ? 'null' : JSON.stringify(before)}/after:{after === null ? 'null' : JSON.stringify(after)}
      </div>
    ))

    render(
      <AuditReviewModal
        request={{ ...PENDING_UPDATE, entityType: 'MODAL_NULL_TEST_ENTITY', actionType: 'CREATE', before: null }}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        onClose={vi.fn()}
      />,
    )

    expect(screen.getByTestId('custom-null-aware')).toBeInTheDocument()
    expect(screen.getByText(/before:null/)).toBeInTheDocument()
    expect(screen.queryByText('（新增，無先前資料）')).not.toBeInTheDocument()
  })

  it('shows requester info and hides approve/reject buttons for an already-reviewed request', () => {
    render(
      <AuditReviewModal
        request={{
          ...PENDING_UPDATE,
          status: 'REJECTED',
          reviewedBy: 'Bob',
          reviewedAt: '2026-07-29T11:00:00',
          rejectReason: '匯率過高',
        }}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        onClose={vi.fn()}
      />,
    )

    expect(screen.getByText(/審核人: Bob/)).toBeInTheDocument()
    expect(screen.getByText(/拒絕原因: 匯率過高/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '核准' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '拒絕' })).not.toBeInTheDocument()
  })

  it('approves a pending request after confirmation', async () => {
    const user = userEvent.setup()
    const onApprove = vi.fn().mockResolvedValue(undefined)
    render(
      <AuditReviewModal request={PENDING_UPDATE} onApprove={onApprove} onReject={vi.fn()} onClose={vi.fn()} />,
    )

    await user.click(screen.getByRole('button', { name: '核准' }))
    expect(screen.getByText('確定要核准此異動申請嗎？')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '確定' }))
    await waitFor(() => expect(onApprove).toHaveBeenCalledWith(1))
  })

  it('shows an inline error and keeps the modal open when approve fails with a re-validation error', async () => {
    const user = userEvent.setup()
    const onApprove = vi
      .fn()
      .mockRejectedValue(new ApiError(409, { error: '此幣種對已存在相同資料' }, 'Conflict'))
    render(
      <AuditReviewModal request={PENDING_UPDATE} onApprove={onApprove} onReject={vi.fn()} onClose={vi.fn()} />,
    )

    await user.click(screen.getByRole('button', { name: '核准' }))
    await user.click(screen.getByRole('button', { name: '確定' }))

    expect(await screen.findByText('此幣種對已存在相同資料')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '核准' })).toBeInTheDocument()
  })

  it('requires a non-empty reason before rejecting', async () => {
    const user = userEvent.setup()
    const onReject = vi.fn()
    render(
      <AuditReviewModal request={PENDING_UPDATE} onApprove={vi.fn()} onReject={onReject} onClose={vi.fn()} />,
    )

    await user.click(screen.getByRole('button', { name: '拒絕' }))
    await user.click(screen.getByRole('button', { name: '確認拒絕' }))

    expect(screen.getByText('請輸入拒絕原因')).toBeInTheDocument()
    expect(onReject).not.toHaveBeenCalled()
  })

  it('rejects a pending request with a reason', async () => {
    const user = userEvent.setup()
    const onReject = vi.fn().mockResolvedValue(undefined)
    render(
      <AuditReviewModal request={PENDING_UPDATE} onApprove={vi.fn()} onReject={onReject} onClose={vi.fn()} />,
    )

    await user.click(screen.getByRole('button', { name: '拒絕' }))
    await user.type(screen.getByLabelText('拒絕原因'), '匯率過高，請重新確認')
    await user.click(screen.getByRole('button', { name: '確認拒絕' }))

    await waitFor(() =>
      expect(onReject).toHaveBeenCalledWith(1, '匯率過高，請重新確認'),
    )
  })
})
