import { useCallback, useEffect, useMemo, useState } from 'react'
import { auditApi } from './auditApi'
import { ApiError } from '../api/client'
import { AuditRequestTable } from './AuditRequestTable'
import { AuditReviewModal } from './AuditReviewModal'
import { useToast } from '../components/ToastProvider'
import type { AuditRequest, AuditStatus } from './types'
import './AuditPage.css'

const NETWORK_ERROR_MESSAGE = '網路錯誤，請稍後再試'
const REQUEST_NOT_FOUND_MESSAGE = '審核申請不存在，請重新整理頁面'
const ALREADY_REVIEWED_MESSAGE = '此申請已被其他人審核過'
const REQUEST_NOT_FOUND_ERROR = 'Audit request not found'
const ALREADY_REVIEWED_ERROR = 'Audit request has already been reviewed'

type StatusFilterValue = AuditStatus | 'ALL'

const STATUS_OPTIONS: { value: StatusFilterValue; label: string }[] = [
  { value: 'PENDING', label: '待審核' },
  { value: 'APPROVED', label: '已核准' },
  { value: 'REJECTED', label: '已拒絕' },
  { value: 'ALL', label: '全部' },
]

function isAuditError(error: ApiError, message: string): boolean {
  return error.body?.error === message
}

export function AuditPage() {
  const { showToast } = useToast()
  const [requests, setRequests] = useState<AuditRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [statusFilter, setStatusFilter] = useState<StatusFilterValue>('PENDING')
  const [entityTypeFilter, setEntityTypeFilter] = useState<string>('ALL')
  const [reviewTarget, setReviewTarget] = useState<AuditRequest | null>(null)

  const fetchRequests = useCallback(async () => {
    setLoading(true)
    setLoadError(false)
    try {
      const data = await auditApi.list({
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        entityType: entityTypeFilter === 'ALL' ? undefined : entityTypeFilter,
      })
      setRequests(data)
    } catch {
      setLoadError(true)
      showToast(NETWORK_ERROR_MESSAGE)
    } finally {
      setLoading(false)
    }
  }, [statusFilter, entityTypeFilter, showToast])

  useEffect(() => {
    fetchRequests()
  }, [fetchRequests])

  const entityTypeOptions = useMemo(
    () => Array.from(new Set(requests.map((request) => request.entityType))).sort(),
    [requests],
  )

  async function handleApprove(id: number) {
    try {
      await auditApi.approve(id)
      setReviewTarget(null)
      showToast('已核准此異動申請', 'success')
      await fetchRequests()
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 404 && isAuditError(error, REQUEST_NOT_FOUND_ERROR)) {
          showToast(REQUEST_NOT_FOUND_MESSAGE)
          setReviewTarget(null)
          await fetchRequests()
          return
        }
        if (error.status === 409 && isAuditError(error, ALREADY_REVIEWED_ERROR)) {
          showToast(ALREADY_REVIEWED_MESSAGE)
          setReviewTarget(null)
          await fetchRequests()
          return
        }
        throw error
      }
      showToast(NETWORK_ERROR_MESSAGE)
    }
  }

  async function handleReject(id: number, reason: string) {
    try {
      await auditApi.reject(id, reason)
      setReviewTarget(null)
      showToast('已拒絕此異動申請', 'success')
      await fetchRequests()
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 404 && isAuditError(error, REQUEST_NOT_FOUND_ERROR)) {
          showToast(REQUEST_NOT_FOUND_MESSAGE)
          setReviewTarget(null)
          await fetchRequests()
          return
        }
        if (error.status === 409 && isAuditError(error, ALREADY_REVIEWED_ERROR)) {
          showToast(ALREADY_REVIEWED_MESSAGE)
          setReviewTarget(null)
          await fetchRequests()
          return
        }
        throw error
      }
      showToast(NETWORK_ERROR_MESSAGE)
    }
  }

  return (
    <div className="audit-page">
      <div className="page-title">
        <h1>審核作業</h1>
      </div>

      <div className="filter-card">
        <div className="filter-row">
          <div className="filter-group">
            <label className="filter-label">類型</label>
            <select
              className="status-filter"
              aria-label="實體類型篩選"
              value={entityTypeFilter}
              onChange={(event) => setEntityTypeFilter(event.target.value)}
            >
              <option value="ALL">全部</option>
              {entityTypeOptions.map((entityType) => (
                <option key={entityType} value={entityType}>
                  {entityType}
                </option>
              ))}
            </select>
          </div>
          <div className="filter-group">
            <label className="filter-label">狀態</label>
            <select
              className="status-filter"
              aria-label="審核狀態篩選"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value as StatusFilterValue)}
            >
              {STATUS_OPTIONS.map((option) => (
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
          <div className="search-table-title">審核申請</div>
        </div>

        <div className="audit-table-wrapper">
          {loading && <div className="table-empty">載入中…</div>}
          {!loading && loadError && (
            <div className="table-empty audit-table-status--error">
              資料載入失敗
              <button type="button" className="btn btn-link" onClick={fetchRequests}>
                重試
              </button>
            </div>
          )}
          {!loading && !loadError && (
            <AuditRequestTable requests={requests} onView={(request) => setReviewTarget(request)} />
          )}
        </div>

        <div className="table-footer">
          <div className="total-count">Total {requests.length} items</div>
        </div>
      </div>

      {reviewTarget && (
        <AuditReviewModal
          request={reviewTarget}
          onApprove={handleApprove}
          onReject={handleReject}
          onClose={() => setReviewTarget(null)}
        />
      )}
    </div>
  )
}
