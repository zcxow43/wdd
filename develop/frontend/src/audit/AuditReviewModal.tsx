import { useState } from 'react'
import type { ReactNode } from 'react'
import { Modal } from '../components/Modal'
import { ApiError } from '../api/client'
import { hasDiffRenderer, renderAuditDiff } from './diffRegistry'
import type { AuditRequest } from './types'
import './AuditReviewModal.css'

interface AuditReviewModalProps {
  request: AuditRequest
  onApprove: (id: number) => Promise<void>
  onReject: (id: number, reason: string) => Promise<void>
  onClose: () => void
}

type ReviewMode = 'view' | 'confirm-approve' | 'reject'

const ACTION_TYPE_LABELS: Record<AuditRequest['actionType'], string> = {
  CREATE: '新增',
  UPDATE: '修改',
  DELETE: '刪除',
}

const APPROVE_FAILED_MESSAGE = '審核失敗，請稍後再試'
const REJECT_FAILED_MESSAGE = '拒絕失敗，請稍後再試'
const REASON_REQUIRED_MESSAGE = '請輸入拒絕原因'

function formatDateTime(value: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    return error.body?.error ?? fallback
  }
  return fallback
}

export function AuditReviewModal({ request, onApprove, onReject, onClose }: AuditReviewModalProps) {
  const [mode, setMode] = useState<ReviewMode>('view')
  const [reason, setReason] = useState('')
  const [approveBusy, setApproveBusy] = useState(false)
  const [rejectBusy, setRejectBusy] = useState(false)
  const [approveError, setApproveError] = useState<string | null>(null)
  const [rejectError, setRejectError] = useState<string | null>(null)

  const isPending = request.status === 'PENDING'

  async function handleConfirmApprove() {
    setApproveBusy(true)
    setApproveError(null)
    try {
      await onApprove(request.id)
    } catch (error) {
      setApproveError(extractErrorMessage(error, APPROVE_FAILED_MESSAGE))
      setMode('view')
    } finally {
      setApproveBusy(false)
    }
  }

  async function handleConfirmReject() {
    if (!reason.trim()) {
      setRejectError(REASON_REQUIRED_MESSAGE)
      return
    }
    setRejectBusy(true)
    setRejectError(null)
    try {
      await onReject(request.id, reason.trim())
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        setRejectError(REASON_REQUIRED_MESSAGE)
      } else {
        setRejectError(extractErrorMessage(error, REJECT_FAILED_MESSAGE))
      }
    } finally {
      setRejectBusy(false)
    }
  }

  function handleCancelReject() {
    setMode('view')
    setReason('')
    setRejectError(null)
  }

  function renderDiffArea(): ReactNode {
    // A dedicated renderer may want to show the real field values on the
    // populated side of a CREATE/DELETE request instead of a blanket
    // placeholder, so it gets the raw before/after (including null)
    // straight away. Entity types without a dedicated renderer keep the
    // original, generic placeholder behavior.
    if (hasDiffRenderer(request.entityType)) {
      return renderAuditDiff(request.entityType, request.before, request.after)
    }
    if (request.before === null) {
      return <div className="audit-diff-placeholder">（新增，無先前資料）</div>
    }
    if (request.after === null) {
      return <div className="audit-diff-placeholder">（將被刪除）</div>
    }
    return renderAuditDiff(request.entityType, request.before, request.after)
  }

  return (
    <Modal title={`審核異動申請 — ${ACTION_TYPE_LABELS[request.actionType]}`} onClose={onClose} size="lg">
      <div className="audit-review-modal">
        <div className="audit-diff-area">{renderDiffArea()}</div>

        <div className="audit-review-meta">
          <span>申請人: {request.requestedBy ?? '—'}</span>
          <span>申請時間: {formatDateTime(request.requestedAt)}</span>
        </div>

        {!isPending && (
          <div className="audit-review-meta">
            <span>審核人: {request.reviewedBy ?? '—'}</span>
            <span>審核時間: {formatDateTime(request.reviewedAt)}</span>
          </div>
        )}

        {request.status === 'REJECTED' && (
          <div className="audit-review-reject-reason">拒絕原因: {request.rejectReason ?? '—'}</div>
        )}

        {isPending && mode === 'view' && (
          <>
            {approveError && (
              <div className="form-error" role="alert">
                {approveError}
              </div>
            )}
            <div className="audit-review-actions">
              <button type="button" className="btn btn-danger" onClick={() => setMode('reject')}>
                拒絕
              </button>
              <button type="button" className="btn btn-primary" onClick={() => setMode('confirm-approve')}>
                核准
              </button>
            </div>
          </>
        )}

        {isPending && mode === 'confirm-approve' && (
          <div className="audit-review-confirm">
            <p className="confirm-message">確定要核准此異動申請嗎？</p>
            <div className="confirm-actions">
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => setMode('view')}
                disabled={approveBusy}
              >
                取消
              </button>
              <button
                type="button"
                className="btn btn-primary"
                onClick={handleConfirmApprove}
                disabled={approveBusy}
              >
                {approveBusy ? '處理中…' : '確定'}
              </button>
            </div>
          </div>
        )}

        {isPending && mode === 'reject' && (
          <div className="audit-review-reject-form">
            <label htmlFor="audit-reject-reason">拒絕原因</label>
            <textarea
              id="audit-reject-reason"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              rows={3}
            />
            {rejectError && <span className="field-error">{rejectError}</span>}
            <div className="confirm-actions">
              <button
                type="button"
                className="btn btn-secondary"
                onClick={handleCancelReject}
                disabled={rejectBusy}
              >
                取消
              </button>
              <button
                type="button"
                className="btn btn-danger"
                onClick={handleConfirmReject}
                disabled={rejectBusy}
              >
                {rejectBusy ? '處理中…' : '確認拒絕'}
              </button>
            </div>
          </div>
        )}
      </div>
    </Modal>
  )
}
