import { useState } from 'react'
import { Modal } from '../components/Modal'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { hasDiffRenderer, renderAuditDiff } from './diffRegistry'
import { ACTION_TYPE_LABELS } from './labels'
import { formatDateTime } from './format'
import type { AuditRequest } from './types'
import './AuditReviewModal.css'

interface AuditReviewModalProps {
  request: AuditRequest
  onClose: () => void
  onApprove: (request: AuditRequest) => Promise<void>
  onReject: (request: AuditRequest, reason: string) => Promise<void>
}

/**
 * Review modal chrome only — the field grid itself is produced entirely by
 * `renderAuditDiff(entityType, before, after)`. This component has no
 * field-specific/entity-specific markup.
 */
export function AuditReviewModal({ request, onClose, onApprove, onReject }: AuditReviewModalProps) {
  const [confirmingApprove, setConfirmingApprove] = useState(false)
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState<string | null>(null)
  const [approveError, setApproveError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const isPending = request.status === 'PENDING'

  const renderDiffArea = () => {
    // A dedicated renderer may opt into receiving `null` directly (e.g. to show the
    // real field values on a CREATE/DELETE request's populated side instead of a
    // blanket placeholder) — but only if one is actually registered for this
    // entityType. Any entityType without one keeps the original, generic behavior
    // unchanged: a static placeholder, never calling into the registry at all.
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

  const handleApproveConfirm = async () => {
    setSubmitting(true)
    setApproveError(null)
    try {
      await onApprove(request)
      setConfirmingApprove(false)
    } catch (error) {
      setConfirmingApprove(false)
      setApproveError(error instanceof Error ? error.message : '發生錯誤，請稍後再試')
    } finally {
      setSubmitting(false)
    }
  }

  const startReject = () => {
    setRejecting(true)
    setReason('')
    setReasonError(null)
  }

  const cancelReject = () => {
    setRejecting(false)
    setReason('')
    setReasonError(null)
  }

  const handleRejectConfirm = async () => {
    const trimmed = reason.trim()
    if (!trimmed) {
      setReasonError('請輸入拒絕原因')
      return
    }
    setSubmitting(true)
    setReasonError(null)
    try {
      await onReject(request, trimmed)
    } catch (error) {
      setReasonError(error instanceof Error ? error.message : '發生錯誤，請稍後再試')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title={`審核異動申請 — ${ACTION_TYPE_LABELS[request.actionType]}`} onClose={onClose} size="lg">
      <div className="audit-review-diff">{renderDiffArea()}</div>

      <div className="audit-review-meta">
        <span>申請人: {request.requestedBy ?? '—'}</span>
        <span>申請時間: {formatDateTime(request.requestedAt)}</span>
      </div>

      {!isPending && (
        <div className="audit-review-meta">
          <span>審核人: {request.reviewedBy ?? '—'}</span>
          <span>審核時間: {request.reviewedAt ? formatDateTime(request.reviewedAt) : '—'}</span>
          {request.status === 'REJECTED' && <span>拒絕原因: {request.rejectReason ?? '—'}</span>}
        </div>
      )}

      {approveError && <div className="field-error field-error--general">{approveError}</div>}

      {isPending && !rejecting && (
        <div className="audit-review-actions">
          <button type="button" className="btn btn-danger" onClick={startReject} disabled={submitting}>
            拒絕
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setConfirmingApprove(true)}
            disabled={submitting}
          >
            核准
          </button>
        </div>
      )}

      {isPending && rejecting && (
        <div className="audit-review-reject">
          <label htmlFor="audit-reject-reason">拒絕原因</label>
          <textarea
            id="audit-reject-reason"
            value={reason}
            onChange={(event) => {
              setReason(event.target.value)
              setReasonError(null)
            }}
            disabled={submitting}
          />
          {reasonError && <span className="field-error">{reasonError}</span>}
          <div className="audit-review-actions">
            <button type="button" className="btn btn-secondary" onClick={cancelReject} disabled={submitting}>
              取消
            </button>
            <button type="button" className="btn btn-danger" onClick={handleRejectConfirm} disabled={submitting}>
              確認拒絕
            </button>
          </div>
        </div>
      )}

      {confirmingApprove && (
        <ConfirmDialog
          message="確定要核准此異動申請嗎？"
          onConfirm={handleApproveConfirm}
          onCancel={() => setConfirmingApprove(false)}
        />
      )}
    </Modal>
  )
}
