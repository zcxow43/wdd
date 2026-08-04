import { useCallback, useEffect, useState } from 'react'
import { useToast } from '../components/ToastProvider'
import { ApiError } from '../api/client'
import { auditApi } from './auditApi'
import { AuditRequestTable } from './AuditRequestTable'
import { AuditReviewModal } from './AuditReviewModal'
import { STATUS_LABELS } from './labels'
import type { AuditRequest, AuditStatus } from './types'
import './AuditPage.css'

type StatusFilter = AuditStatus | 'ALL'
const ENTITY_TYPE_ALL = 'ALL'

const STATUS_FILTER_OPTIONS: Array<{ value: StatusFilter; label: string }> = [
  { value: 'PENDING', label: STATUS_LABELS.PENDING },
  { value: 'APPROVED', label: STATUS_LABELS.APPROVED },
  { value: 'REJECTED', label: STATUS_LABELS.REJECTED },
  { value: 'ALL', label: '全部' },
]

const AUDIT_REQUEST_NOT_FOUND_MESSAGE = 'Audit request not found'
const AUDIT_REQUEST_ALREADY_REVIEWED_MESSAGE = 'Audit request has already been reviewed'

export function AuditPage() {
  const { showToast } = useToast()
  const [requests, setRequests] = useState<AuditRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('PENDING')
  const [entityTypeFilter, setEntityTypeFilter] = useState<string>(ENTITY_TYPE_ALL)
  // Every entityType ever seen from a successful load, so the dropdown's option list
  // never shrinks just because the currently-applied entityType filter narrows the
  // visible rows down to a subset.
  const [knownEntityTypes, setKnownEntityTypes] = useState<string[]>([])
  const [reviewTarget, setReviewTarget] = useState<AuditRequest | null>(null)

  const fetchRequests = useCallback(async () => {
    setLoading(true)
    try {
      const data = await auditApi.list({
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        entityType: entityTypeFilter === ENTITY_TYPE_ALL ? undefined : entityTypeFilter,
      })
      setRequests(data)
      setKnownEntityTypes((current) =>
        Array.from(new Set([...current, ...data.map((request) => request.entityType)])),
      )
    } catch {
      showToast('網路錯誤，請稍後再試')
    } finally {
      setLoading(false)
    }
  }, [statusFilter, entityTypeFilter, showToast])

  useEffect(() => {
    fetchRequests()
  }, [fetchRequests])

  const handleView = (request: AuditRequest) => setReviewTarget(request)
  const closeReviewModal = () => setReviewTarget(null)

  const handleApprove = async (request: AuditRequest) => {
    try {
      await auditApi.approve(request.id)
      showToast('已核准', 'success')
      closeReviewModal()
      await fetchRequests()
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.message === AUDIT_REQUEST_NOT_FOUND_MESSAGE) {
          showToast('審核申請不存在，請重新整理頁面')
          closeReviewModal()
          await fetchRequests()
          return
        }
        if (error.message === AUDIT_REQUEST_ALREADY_REVIEWED_MESSAGE) {
          showToast('此申請已被其他人審核過')
          closeReviewModal()
          await fetchRequests()
          return
        }
        // Handler re-validation failure (400/404/409): surface the API's own
        // message inline in the modal and leave it open so the reviewer can reject
        // instead if the change is no longer valid.
        throw error
      }
      showToast('網路錯誤，請稍後再試')
    }
  }

  const handleReject = async (request: AuditRequest, reason: string) => {
    try {
      await auditApi.reject(request.id, reason)
      showToast('已拒絕', 'success')
      closeReviewModal()
      await fetchRequests()
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.message === AUDIT_REQUEST_NOT_FOUND_MESSAGE) {
          showToast('審核申請不存在，請重新整理頁面')
          closeReviewModal()
          await fetchRequests()
          return
        }
        if (error.message === AUDIT_REQUEST_ALREADY_REVIEWED_MESSAGE) {
          showToast('此申請已被其他人審核過')
          closeReviewModal()
          await fetchRequests()
          return
        }
        if (error.status === 400) {
          throw new Error('請輸入拒絕原因')
        }
        throw error
      }
      showToast('網路錯誤，請稍後再試')
    }
  }

  return (
    <div className="audit-page">
      <h1 className="page-title">審核作業</h1>

      <div className="filter-card">
        <div className="filter-row">
          <div className="filter-group audit-filter">
            <label htmlFor="audit-filter-entity-type">類型:</label>
            <select
              id="audit-filter-entity-type"
              className="filter-input"
              value={entityTypeFilter}
              onChange={(event) => setEntityTypeFilter(event.target.value)}
            >
              <option value={ENTITY_TYPE_ALL}>全部</option>
              {knownEntityTypes.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
          </div>

          <div className="filter-group audit-filter">
            <label htmlFor="audit-filter-status">狀態:</label>
            <select
              id="audit-filter-status"
              className="filter-input"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
            >
              {STATUS_FILTER_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      <div className="search-table-card">
        <div className="search-table-header">
          <div className="search-table-title">
            <span>審核申請列表</span>
          </div>
        </div>

        <AuditRequestTable requests={requests} loading={loading} onView={handleView} />

        <div className="table-footer">
          <div className="total-count">Total {requests.length} items</div>
        </div>
      </div>

      {reviewTarget && (
        <AuditReviewModal
          request={reviewTarget}
          onClose={closeReviewModal}
          onApprove={handleApprove}
          onReject={handleReject}
        />
      )}
    </div>
  )
}
